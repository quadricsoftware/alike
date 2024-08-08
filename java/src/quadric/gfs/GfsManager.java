package quadric.gfs;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.ods.JournalManager;
import quadric.ods.VmVersion;
import quadric.ods.dao.Crud;
import quadric.ods.dao.Dao;
import quadric.ods.dao.DaoFactory;
import quadric.ods.dao.GfsAdHocCrud;
import quadric.ods.dao.GfsPolicyCrud;
import quadric.ods.dao.GfsVersionCrud;

public class GfsManager {
	private static final Logger LOGGER = LoggerFactory.getLogger( GfsManager.class.getName() );
	
	private static GfsManager me = new GfsManager();
	
	private Dao dao;
	//private String installId;
	
	private GfsManager() {
		;
	}
	
	public static GfsManager instance() {
		return me;
	}
	
	public void init() {
		String dbLocation = VaultSettings.instance().getLocalDbPath() + File.separator + "gfs.db";
		dao = DaoFactory.instance().create(dbLocation);
	}
	
	
	public void markVersion(VmVersion v, int schedId) {
		LOGGER.debug("Retention manager marking version " + v + " for schedule " + schedId);
		String myInstallId = VaultSettings.instance().getSettings().get("dsid" + v.getSiteId());
		try (Connection con = dao.getWriteConnection()) {
			GfsVersionCrud crud = new GfsVersionCrud(con);
			GfsVersion gfs = new GfsVersion(v, myInstallId, schedId);
			crud.create(gfs);
			con.commit();
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
	}
	
	public void unmarkVersion(VmVersion v) {
		LOGGER.debug("Retention manager unmarking version " + v);
		String myInstallId = VaultSettings.instance().getSettings().get("dsid" + v.getSiteId());
		try (Connection con = dao.getWriteConnection()) {
			GfsVersionCrud crud = new GfsVersionCrud(con);
			GfsVersion gfs = new GfsVersion(v, myInstallId, -1);
			crud.delete(gfs);
			con.commit();
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
	}
	
	public void markVersion(VmVersion v, int siteId, int schedId, String pathToInfoFile) {
		SchedKey k = new SchedKey();
		String myInstallId = VaultSettings.instance().getSettings().get("dsid" + v.getSiteId());
		k.installId = myInstallId;
		k.sid = schedId;
		k.siteId = siteId;
		
		List<GfsPolicy> policies = listPoliciesByScheduleImpl(k);
		if(policies.isEmpty() || policies.get(0).gfs == Gfs.gfsAdHoc) {
			k.sid = 0;
		}
		Set<Long> versions = getAllVersionsForVm(k, v.getPlatformStyleUuid());
		TreeMap<Long,Integer> tempPooled = new TreeMap<Long,Integer>();
		getPooledVersions(policies, versions, tempPooled, getAdHocMax(v));
		markVersion(v, schedId);
		
		// TODO: Place 
		
	}
	
	public int getAdHocMax(VmVersion v) {
		try (Connection con = dao.getReadOnlyConnection()) {
			GfsAdHocCrud crud = new GfsAdHocCrud(con);
			GfsAdHoc hoc = crud.querySingle(crud.buildQuery("SELECT * FROM gfs_adhoc WHERE uuid=? AND siteId=?", v.getPlatformStyleUuid(), v.getSiteId()));
			return hoc.getQuantity();
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
	}
	
	public void purge(SchedKey key, List<VmVersion> backupSet, SortedMap<Long, Integer> pooled, Consumer<List<VmVersion>> purgeCallback) {
		String uuid = backupSet.get(0).getPlatformStyleUuid();
		// Pull all known versions out for this guy from the GFS database
		Set<Long> knownVersions = getAllVersionsForVm(key, uuid);
		int maxVersionsAdHoc = getAdHocMax(backupSet.get(0));
		// intercect of "retained" and "known"
		List<Long> coolGuy = backupSet.stream().map(v -> v.getVersion()).collect(Collectors.toList());
		Set<Long> inVersions = new HashSet<Long>(coolGuy);
		inVersions.retainAll(knownVersions);
		
		// Figure out the policy
		List<GfsPolicy> policies = listPoliciesByScheduleImpl(key);
		// Run GFS policies for the remaining set
		TreeMap<Long,Integer> tempPooled = new TreeMap<Long,Integer>();
		getPooledVersions(policies, inVersions, tempPooled, maxVersionsAdHoc);
		List<VmVersion> toDelete = new ArrayList<VmVersion>();
		for(Long l : inVersions) {
			if(tempPooled.containsKey(l) == false) {
				VmVersion vv = new VmVersion(backupSet.get(0));
				vv.setVersion(l);
				toDelete.add(vv);
				unmarkVersion(vv);
			}
		}
		purgeCallback.accept(toDelete);
		getPooledVersions(policies, inVersions, pooled, maxVersionsAdHoc);
		
	}
	
	public Set<Long> getAllVersionsForVm(SchedKey k, String uuid) {
		try (Connection con = dao.getReadOnlyConnection()) {
			GfsVersionCrud crud = new GfsVersionCrud(con);
			ResultSet rs = crud.buildQuery("SELECT epoch FROM gfs_version WHERE scheduleId=? AND installId=? AND siteId=? AND uuid=?", k.sid, k.installId, k.siteId, uuid);
			List<GfsVersion> vers = crud.query(rs);
			Set<Long> cruft = new TreeSet<Long>();
			List<Long> crufty = vers.stream().map(v -> v.getEpoch()).collect(Collectors.toList());
			cruft.addAll(crufty);
			return cruft;
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
	}
	
	public void getPooledVersions(final List<GfsPolicy> policies2, 
							final Set<Long> inVersionsImmutable2, SortedMap<Long,Integer> pooled, int maxVersionsAdHoc) {
		Set<Long> inVersionsImmutable = Collections.unmodifiableSet(inVersionsImmutable2);
		// Sort in proper order of GFS policy, starting from dailies on up
		Comparator<GfsPolicy> compy = (GfsPolicy o1, GfsPolicy o2) -> Integer.compare(o1.gfs.val, o2.gfs.val);
		List<GfsPolicy> policies = new ArrayList<GfsPolicy>(policies2);
		Collections.sort(policies, compy);
				
				
		TreeMap<Long,Gfs> dailies = new TreeMap<Long,Gfs>();
		TreeMap<Long,Gfs> weeklies = new TreeMap<Long,Gfs>();
		TreeMap<Long,Gfs> monthlies = new TreeMap<Long,Gfs>();
		TreeMap<Long,Gfs> yearlies = new TreeMap<Long,Gfs>();
		TreeMap<Long,Gfs> adHoc = new TreeMap<Long,Gfs>();
		if(policies.isEmpty()) {
			// Try ad-hoc stylings
			runAdHoc(maxVersionsAdHoc, inVersionsImmutable, adHoc);
		} else {
			runGfs(inVersionsImmutable, policies, dailies, weeklies, monthlies, yearlies);
		}
		// Condense
		pool(dailies, pooled);
		pool(weeklies, pooled);
		pool(monthlies, pooled);
		pool(yearlies, pooled);
		pool(adHoc, pooled);
		
		// Add "candidate" logic so people running GFS
		// get a "candidate" flag on their unmatched versions
		if(policies.isEmpty()) {
			for(Map.Entry<Long,Integer> i : pooled.entrySet()) {
				if(i.getValue() == GfsMask.gfsEmptyMask.mask) {
					i.setValue(GfsMask.gfsCandidateMask.mask);
				}
			}
		}
	}
	
	
	private void runAdHoc(int maxVersions, Set<Long> inVersions, Map<Long,Gfs> adHocRetained) {
		GfsPolicy evil = new GfsPolicy();
		evil.versions = maxVersions;
		evil.gfs = Gfs.gfsAdHoc;
		TreeMap<Long,Gfs> primordial = new TreeMap<Long,Gfs>();
		for(Long v : inVersions) {
			primordial.put(v, Gfs.gfsAdHoc);
		}
		matchQuantity(primordial, evil, adHocRetained);
	}
	
	private void matchQuantity(final SortedMap<Long,Gfs> inMap2, final GfsPolicy policy, Map<Long,Gfs> out) {
		SortedMap<Long,Gfs> inMap = Collections.unmodifiableSortedMap(inMap2);
		int count = policy.versions;
		if(count > inMap.size()) {
			count = inMap.size();
		}
		TreeMap<Long,Gfs> reverseMap = new TreeMap<Long,Gfs>(Collections.reverseOrder());
		reverseMap.putAll(inMap);
		// Only copy over only retained versions, latest to earliest, skip everything else
		for(Map.Entry<Long,Gfs> kv : reverseMap.entrySet()) {
			if(out.size() == count) {
				break;
			}
			out.put(kv.getKey(), kv.getValue());
		}
	}
	
	private void runGfs(final Set<Long> inVersions2, final List<GfsPolicy> policies,
			TreeMap<Long,Gfs> dailiesRetained, 
			TreeMap<Long,Gfs> weekliesRetained, 
			TreeMap<Long,Gfs> monthliesRetained, 
			TreeMap<Long,Gfs> yearliesRetained) {
		
		Set<Long> inVersions = Collections.unmodifiableSet(inVersions2);
		if(policies.size() < 4) {
			throw new IllegalStateException("GFS not completely configured for schedule");
		}
		
		TreeMap<Long,Gfs> primordial = new TreeMap<Long,Gfs>();
		TreeMap<Long,Gfs> dailies = new TreeMap<Long,Gfs>();
		TreeMap<Long,Gfs> weeklies = new TreeMap<Long,Gfs>();
		TreeMap<Long,Gfs> monthlies = new TreeMap<Long,Gfs>();
		TreeMap<Long,Gfs> yearlies = new TreeMap<Long,Gfs>();
		GfsPolicy dummy = new GfsPolicy();
		dummy.card = 0;
		dummy.gfs = Gfs.gfsNone;
		// Prepopulate primoridal
		inVersions.stream().forEach(v -> primordial.put(v, Gfs.gfsNone) );
		runRules(primordial, policies.get(0), dummy, dailies);
		runRules(dailies, policies.get(1), dummy, weeklies);
		
		if(policies.get(2).gfs == Gfs.gfsMonthly) {
			runRules(weeklies, policies.get(2), policies.get(1), monthlies);
		} else if(policies.get(2).gfs == Gfs.gfsMonthlyDaily) {
			runRules(dailies, policies.get(2), dummy, monthlies);
		} else {
			throw new IllegalStateException("Policy is invalid");
		}
		runRules(monthlies, policies.get(3), dummy, yearlies);
		// Enforce quantity restrictions
		matchQuantity(dailies, policies.get(0), dailiesRetained);
		matchQuantity(weeklies, policies.get(1), weekliesRetained); 
		matchQuantity(monthlies, policies.get(2), monthliesRetained);
		matchQuantity(yearlies, policies.get(3), yearliesRetained);
	}
	
	private void runRules(final TreeMap<Long,Gfs> inMap2, final GfsPolicy policy, final GfsPolicy child, TreeMap<Long,Gfs> outMap) {
		SortedMap<Long,Gfs> inMap = Collections.unmodifiableSortedMap(inMap2);
		TreeMap<Long,Gfs> temp = new TreeMap<Long,Gfs>();
		matchProgeny(inMap, policy, temp);
		matchCardinality(temp, policy, child, outMap);
	}
	
	private void matchProgeny(final SortedMap<Long,Gfs> inMap2, final GfsPolicy policy, TreeMap<Long,Gfs> outMap) {
		SortedMap<Long,Gfs> inMap = Collections.unmodifiableSortedMap(inMap2);
		Gfs target = Gfs.gfsNone;
		switch(policy.gfs) {
			case gfsWeekly:
			case gfsMonthlyDaily: 
				target = Gfs.gfsDaily;
				break;
			case gfsMonthly:
				target = Gfs.gfsWeekly;
				break;
			case gfsYearly:
				target = Gfs.gfsMonthly;
				break;
			default:
				break;
		}
		for(Map.Entry<Long,Gfs> e: inMap.entrySet()) {
			Gfs val = e.getValue();
			// Copy over generational matches, or everything if we're not dependent on an earlier generation
			if(val == target || target == Gfs.gfsNone || (target == Gfs.gfsMonthly && val == Gfs.gfsMonthlyDaily)) {
				outMap.put(e.getKey(), policy.gfs);
			}
		}
	}
	
	private void matchCardinality(final SortedMap<Long,Gfs> inMap2, final GfsPolicy policy, final GfsPolicy childPolicy, TreeMap<Long,Gfs> out) {
		SortedMap<Long,Gfs> inMap = Collections.unmodifiableSortedMap(inMap2);
		if(policy.gfs == Gfs.gfsNone || policy.gfs == Gfs.gfsAdHoc) {
			// Not actually GFS!
			return;
		}
		
		GfsSpans spans = new GfsSpans();
		placeVersionsInSpans(inMap, policy.gfs, spans);
		boolean useFirstOrLast = false;
		boolean useFirst = true;
		switch(policy.gfs) {
			case gfsDaily:
			case gfsMonthlyDaily:
			case gfsMonthly:
				useFirstOrLast = true;
				if(policy.card != 0) {
					useFirst = false;
				}
				break;
			case gfsWeekly:
				// For debugging
				break;
			default: 
		}
		for(Map.Entry<Long, List<Long>> e : spans.spans.entrySet()) {
			int targetNum = policy.card;
			if(useFirstOrLast && useFirst) {
				targetNum = 0;
			} else if(useFirstOrLast) {
				if(policy.gfs == Gfs.gfsDaily) {
					// If they always want the last backup of the day, 
					// we just grab the last one made in the schedule and promote it.
					targetNum = e.getValue().size() -1;
				} else {
					int dayOfWeek = childPolicy.card;
					targetNum = countPotentialOccurencesInMonthly(policy.gfs, dayOfWeek, e.getKey()) -1;
				}
			}
			// Iterate over backup versions in each span bucket
			int count = 0;
			boolean foundMatch = false;
			List<Long> guys = e.getValue();
			for(Long l : guys) {
				/*if(policy.gfs == Gfs.gfsMonthly) {
					System.out.println("Looking at candidate " + count + "/" + targetNum + " backup from " + GfsDateUtil.fromEpoch(l));
				}*/
				if(count >= targetNum) {
					// Cardinal match of Mth backup occurred
					if(l != 0) {
						// Copy over, but skip placeholders
						out.put(l, policy.gfs);
						foundMatch = true;
						break;
					}
				}
				count++;
			}
			if(foundMatch == true) {
				continue;
			}

			// We couldn't find ANYTHING. Try walking backwards, but only if there are more
			// spans after us
			for(Map.Entry<Long,List<Long>> nexto = spans.spans.higherEntry(e.getKey()); 
					nexto != null && foundMatch == false; 
					nexto = spans.spans.higherEntry(nexto.getKey())) {
				List<Long> backwards = new ArrayList<Long>(nexto.getValue());
				Collections.reverse(backwards);
				for(Long l : backwards) {
					// Cardinal match of Mth backup occurred
					if(l != 0) {
						// Copy over, but skip placeholders
						out.put(l, policy.gfs);
						foundMatch = true;
						break;
					}
				}
			}
		}
	}
	
	
	
	/*
	 * Count the number of potential occurance "slots" in a monthly backup.
	 * For traditional monthlies, we need to know the number of times the weekly could occur,
	 * but for monthly-dailies, we just need to know the max number of dailies that could occur, which 
	 * is equal to the number of days in the month
	 * 
	 */
	private int countPotentialOccurencesInMonthly(final Gfs gfs, int dayOfWeek, long spanBeginTime) {
		if(gfs == Gfs.gfsMonthly || gfs == Gfs.gfsMonthlyDaily) {
			;
		} else {
			throw new IllegalStateException("Preconditions not met");
		}
		GfsDateUtil.sanityCheckDate(spanBeginTime);
		LocalDate d = GfsDateUtil.fromEpoch(spanBeginTime);
		//System.out.println("Hello and welcome to countPot monthly for " + d);
		if(gfs == Gfs.gfsMonthlyDaily) {
			return d.lengthOfMonth();
		}
		if(gfs == Gfs.gfsMonthly) {
			// Count the number of times their backup weekly day appears in the month
			// Walk backwards from end of month
			d = d.with(ChronoField.DAY_OF_MONTH, d.lengthOfMonth());
			int count = 0;
			int monthOfYear = d.getMonthValue();
			while(monthOfYear == d.getMonthValue()) {
				if(GfsDateUtil.getDayOfWeek(d) == dayOfWeek) {
					count++;
				}
				d = d.minusDays(1);
			}
			return count;
		}
		throw new IllegalStateException("Cannot determine occurances for this schedule type");
	}
	
	/*
	 * Walks backwards on the calendar from the date provided, finding the first earlier acceptable time, or zero if no earlier time is available.
	 */ 
	private long getPreviousBackupInterval(long span, long lastBackup, Gfs gfs) {
		GfsDateUtil.sanityCheckDate(lastBackup);
		LocalDate goodTime = GfsDateUtil.fromEpoch(lastBackup);
		if(gfs == Gfs.gfsDaily) {
			return 0;
		} else if(gfs == Gfs.gfsWeekly || gfs == Gfs.gfsMonthlyDaily) {
			// Weeklies and monthlyDailies are denominated in days
			goodTime = goodTime.minusDays(1);
		} else if(gfs == Gfs.gfsMonthly) {
			// Classic monthlies are denominated in weeks
			goodTime = goodTime.minusDays(7);
		} else if(gfs == Gfs.gfsYearly) {
			// Yearlies are denominated in months
			goodTime = goodTime.minusMonths(1);
		}
		long awesome = GfsDateUtil.toEpoch(goodTime);
		if(awesome >= span) { 
			return awesome;
		} else {
			return 0;
		}
	}
	
	private void placeVersionsInSpans(final SortedMap<Long,Gfs> inMap, Gfs gfs, GfsSpans spans) {
		//System.out.println("PLACE VERSIONS IN SPANS**********");
		for(Map.Entry<Long,Gfs> e: inMap.entrySet()) {
			/*if(gfs == Gfs.gfsMonthly) {
				System.out.println("OKAY SARGE BEGINNIN THE FUN************");
			}*/
			long span = GfsSpans.getSpanStart(e.getKey(), gfs);
			List<Long> versions = spans.get(span);
			versions.add(e.getKey());
			/*if(gfs == Gfs.gfsMonthly) {
				System.out.println("Adding backup from " + GfsDateUtil.instaFormat(e.getKey()) + " to span " + GfsDateUtil.instaFormat(span));
			}*/

		}
		// Second pass to put placeholders in the span representing not-backed up versions
		if(gfs == Gfs.gfsDaily) return;
		for(Map.Entry<Long, List<Long>> e: spans.spans.entrySet()) {
			List<Long> versions = e.getValue();
			if(versions.isEmpty()) {
				continue;
			}
			long spanStart = 0;
			ListIterator<Long> guy = versions.listIterator();
			while(guy.hasNext()) {
				int spot = guy.nextIndex();
				ListIterator<Long> tempy = versions.listIterator(spot);
				long curVersionVal = guy.next();
				spanStart = curVersionVal;
				// Find previous non-placeholder entry, if any
				for(long val = curVersionVal; tempy.hasPrevious(); val = tempy.previous()) {
					if(val != 0) {
						//if(gfs == Gfs.gfsMonthly) {
							//System.out.println("SpanStart is " + GfsDateUtil.instaFormat(val));
						//}
						spanStart = val;
						break;
					}
				}
			
				// We need to insert some zero-timestamps as placeholders if there are 
				// spans that don't have versions associated with them
				long firstPossible = curVersionVal;
				/*if(gfs == Gfs.gfsMonthly) {
					System.out.println("firstPossible is " + GfsDateUtil.instaFormat(firstPossible));
				}*/
				while(firstPossible != 0) {
					/*if(gfs == Gfs.gfsMonthly) {
						System.out.println("Looking for possible placeholders for " 
								+ versions.stream().map( i -> GfsDateUtil.instaFormat(i)).collect(Collectors.joining(",")));
					}*/
					firstPossible = getPreviousBackupInterval(spanStart, firstPossible, gfs);
					if(firstPossible == 0) { 
						break;
					}
					// Insert a placeholder value
					guy.previous();
					guy.add(0L);
					guy.next();
					
					
					
					if(gfs == Gfs.gfsMonthly) {
						System.out.println("Inserting placeholder at " + GfsDateUtil.instaFormat(firstPossible) + ", we are now " 
								+ versions.stream().map( i -> GfsDateUtil.instaFormat(i)).collect(Collectors.joining(",")));
					}
				}
			}
		}
	}
	
	/*
	 * Take all items in inMap and OR them into any existing items in pooledVersionMap
	 */
	private void pool(final SortedMap<Long, Gfs> inMap2, SortedMap<Long, Integer> pooledVersionMap) {
		SortedMap<Long,Gfs> inMap = Collections.unmodifiableSortedMap(inMap2);
		for(Map.Entry<Long,Gfs> e : inMap.entrySet()) {
			int bitmap = pooledVersionMap.getOrDefault(e.getKey(), 0);
			int oldVal = GfsMask.gfsToMask(e.getValue()).mask;
			bitmap = bitmap | oldVal;
			pooledVersionMap.put(e.getKey(), bitmap);
		}
	}
	
	List<GfsPolicy> listPoliciesByScheduleImpl(SchedKey inKey) {
		//
		// We have a problem where GFS schedules apply to both onsite and offsite. In order to make this work,
		// we're going to fudge this and pull the GFS schedule for the onsite when they want offsite.
		SchedKey key = new SchedKey(inKey);
		try (Connection con = dao.getReadOnlyConnection()) {
			GfsPolicyCrud crud = new GfsPolicyCrud(con);
			ResultSet rs = crud.buildQuery("SELECT gi.* from gfs_instance gi, gfs_schedule gs WHERE scheduleId=? AND installId=? AND siteId=? AND gs.gfsId = gi.gfsId", 
													key.sid, key.installId, key.siteId);
			List<GfsPolicy> policies = crud.query(rs);
			return policies;
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
		
	}
	
	
}
