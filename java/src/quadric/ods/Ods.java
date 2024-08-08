package quadric.ods;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

import quadric.blockvaulter.BandwidthCloudAdapter;
import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.KurganCommander;
import quadric.blockvaulter.VaultSettings;
import quadric.crypt.CryptUtil;
import quadric.legacy.NimbusDbListener;
import quadric.legacy.NimbusVersion;
import quadric.ods.HeaderManager.HeaderEvent;
import quadric.ods.HeaderManager.HeaderEventType;
import quadric.ods.dao.Crud;
import quadric.ods.dao.Dao;
import quadric.ods.dao.DaoFactory;
import quadric.ods.dao.PrintCrud;
import quadric.restore.BadBlockManager;
import quadric.restore.VersionLocker;
import quadric.restore.VmVersionKey;
import quadric.stats.StatsManager;
import quadric.util.BandwidthMeter;
import quadric.util.HclCursor;
import quadric.util.HclReaderUtil;
import quadric.util.HclWriterUtil;
import quadric.util.IteratorCursor;
import quadric.util.JobControl;
import quadric.util.Pair;
import quadric.util.PathedGetResult;
import quadric.util.Print;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;


/**
 * The entrypoint for vaulting-specific logic and composite of lower-level vaulting facilities.
 * Compare to the C++ ClusterServer class.
 *
 */
public class Ods {
	
	@SuppressWarnings("serial")
	public static class SyncException extends Exception { 
		SyncException(String string) {
			super(string);
		}
	}
	
	/**
	 * Represents an active cursor on a vault with I/O in progress
	 *
	 */
	public class VaultTx implements Closeable {
		int vaultTx;
		long headerTx;
		public VmVersion info;
		String eclLocation;
		int total;
		VaultTx parent = null;
		boolean meOrChildrenAborted = false;
		boolean hasCommittedAlready = false;
		
		VaultTx(int vaultTx, long headerTx, String eclLocation, VmVersion info) {
			this.vaultTx = vaultTx;
			this.headerTx = headerTx;
			this.info = info;
			this.eclLocation = eclLocation;
			total = vManager.revealCursor(vaultTx).count();
		}
		
		/**
		 * Obtain the next print to vault to offsite
		 * @return the next print to vault, or null if this is the end
		 */
		public Print vaultNext() {
			return vManager.vaultNext(vaultTx);
		}
		
		/**
		 * Wrap up this vault after all vaults have been sent
		 * @param progress
		 */
		public void commit(DoubleConsumer progress) {
			if(vManager.vaultNext(vaultTx) != null) {
				throw new CloudException("Attempt to commit a vault that isn't finished sending yet");
			}
			hasCommittedAlready = true;
			if(vManager.unregister(vaultTx) == false) {
				if(meOrChildrenAborted == true) {
					LOGGER.info("Transaction set to aborted; will not commit");
					return;
				}
				if(parent != null && parent.meOrChildrenAborted == true) {
					LOGGER.info("Parent transaction set to aborted; will not commit child");
					return;
				}
				LOGGER.debug( "Committing transaction header " + headerTx);
				commitVault(headerTx, eclLocation, info, progress);
			}
		}
		
		/**
		 * Obtains the current number of prints sent
		 * @return
		 */
		public int getVaultedPrintCount() {
			return vManager.revealCursor(vaultTx).getPosition();
		}
		
		/**
		 * Sum of all prints sent, sending, or yet sent for this vault
		 * @return count
		 */
		public int getTotal() {
			return total;
		}
		
		public long getTx() {
			return headerTx;
		}
		
		public VmVersion getVersion() {
			return info;
		}
		
		/**
		 * Closes and aborts this vault unless it's been committed already
		 */
		public void close() throws IOException {
			if(hasCommittedAlready) {
				// They already committed. We are fine
				return;
			}
			// Otherwise rollback
			//Exception eee = new Exception();
			//LOGGER.error("Transaction " + headerTx + " (child tx " + vaultTx + ") aborting! Unthrown stack is: ", eee);
			// Set ourselves to aborted
			meOrChildrenAborted = true;
			// Set parent to aborted
			if(parent != null) {
				parent.meOrChildrenAborted = true;
			}
			vManager.unregister(vaultTx);
		}
		
	}
	
	private static final Logger LOGGER = LoggerFactory.getLogger( Ods.class.getName() );
	//private static final int MAINT_PURGE_BATCH_SIZE = 500;
	
	private static final int FILE_BUFFER_SIZE = 4096 *2;
	public static final int LOCK_DURATION_SECS = 10 * 60;
	public static final float RECON_BANDWIDTH_RATIO = .4F;
	public static final String ODS_TYPE_FILE_NAME = "ods.type";
	public static final String PURGE_FILE_NAME = "purged.dcl";
	public static final int BACKGROUND_PURGE_INTERVAL_MINUTES = 3;
	public static final int DOWNLOAD_MIN_BYTES_SEC = 12500;
	public static final int UPLOAD_MIN_BYTES_SEC = 12500;
	private static final int ECL_REBUILD_CACHE_PRINTS_SIZE = 1024 * 100;
	
	
	private int maxConflictPrints;
	private HeaderManager hManager;
	private JournalManager jManager;
	private BadBlockManager badBlockMan;
	private VaultManager vManager;
	private int siteId = -1;
	private String ownerId = "";
	//private CloudAdapter adp;
	//private Boolean hasGoodTimestamp = null;
	private String dbPath;
	//private NimbusDbListener nimbusDbListener;
	private long currentLargestTx = 0;
	private int deleteThreadCount;
	private volatile boolean isNuked = true;
	private boolean isFirstSync = true;
	private AtomicLong sentBlocks = new AtomicLong();
	private AtomicLong sentBlockSize = new AtomicLong();
	private volatile boolean hasShutDown = false;
	private volatile boolean maintRunning = false;
	private boolean hasEverSynced = false;
	
	
	
	/**
	 * Construct a new ODS with associated database resources
	 * @param siteId the siteId to construct
	 */
	public Ods(int siteId) {
		this.maxConflictPrints = VaultSettings.instance().getMaxConflictPrints(siteId);
		this.deleteThreadCount = VaultSettings.instance().getDeleteThreadCount(siteId);
		this.siteId = siteId;
		//adp = VaultSettings.instance().getAdapter(siteId);
		this.hManager = new HeaderManager(siteId);
		dbPath = VaultSettings.instance().getRemoteDbPath();
		dbPath += File.separator + siteId + "ods.db";
		LOGGER.info("Iniitalizing ODS connection " + siteId + " with " + deleteThreadCount 
				+ " delete threads and " 
				+ maxConflictPrints + " max conflict prints");
		this.jManager = new JournalManager(siteId, dbPath);
		this.vManager = new VaultManager(siteId);
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		if(adp.stat("owner.id") == true) {
			ownerId = VaultUtil.getBlockToString(adp, "owner.id");
		} 
		if(this.isReadOnly()) {
			LOGGER.info("*********Data Store " + siteId + " is READ-ONLY************");
		}
		hManager.addListener(NimbusDbListener.instance());
		badBlockMan = new BadBlockManager(siteId);
		loadBlockStats();
		// Avoid divide by zero
		if(sentBlocks.get() == 0) {
			sentBlocks.getAndIncrement();
			sentBlockSize.set(300 * 1024);
		}
		final long blockSize = VaultSettings.instance().makeKurganSets().blockSizeBytes;
		StatsManager.instance().register(siteId + ".blockCountSent", () -> sentBlocks.get() );
		StatsManager.instance().register(siteId + ".blockDataSent", () -> sentBlockSize.get() );
		StatsManager.instance().register(siteId + ".avrCompressedBlock", () -> (sentBlockSize.get() / sentBlocks.get()) );
		StatsManager.instance().register(siteId + ".blockSize", () -> (blockSize) );
		
	}
	
	
	
	public void shutdown() {
		if(hasShutDown == true) {
			return;
		}
		hasShutDown = true;
		if(isReadOnly()) {
			return;
		}
		badBlockMan.shutdown();
		Stopwatch watchy = new Stopwatch();
		if(isNuked) {
			LOGGER.info("Shutdown waiting on journaling transaction, please wait...");
		}
		while(isNuked) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				;
			}
			if(watchy.getElapsed(TimeUnit.SECONDS) > 30) {
				break;
			}
		}
		boolean hasCleanedUp = false;
		if(isNuked == false) {
			synchronized(this) {
				if(isNuked == false) {
					hManager.setTransactMd5(hManager.getRemoteMd5());
					hasCleanedUp = true;
				}
			}
		}
		if(hasCleanedUp == false) {
			LOGGER.error("Timeout waiting on sync state for ds " + siteId + ", rebuild will be required on start-up.");
		} else {
			LOGGER.info("Graceful shutdown successfully sync state for site " + siteId + " with md5 of " + hManager.getRemoteMd5());
		}
		jManager = null;
		hManager = null;
		storeBlockStats();
	}
	
	public boolean isReadOnly() {
		return VaultSettings.instance().getAdapter(siteId).isReadOnly();
	}
	
	public long getCurrentLargestTx() {
		return currentLargestTx;
	}
	
	public HeaderManager revealHeaderManager() {
		return hManager;
	}
	
	public BadBlockManager revealBadBlockManager() {
		return badBlockMan;
	}
	
	public void sentBlock(int size) {
		sentBlocks.getAndIncrement();
		sentBlockSize.addAndGet(size);
	}
	
	private void loadBlockStats() {
		String path = "/mnt/ads/" + siteId + ".thru";
		if(new File(path).exists() == false) {
			 return;
		}
		try (BufferedReader br = new BufferedReader(new FileReader(path))) {
			String theLine = br.readLine();
			String [] awesome = theLine.split(",");
			if(awesome == null || awesome.length < 2) {
				LOGGER.error(path + " not a valid block throughput file, dedup stat will be inaccurate");
			}
			try {
				this.sentBlocks.set(Long.parseLong(awesome[0]));
				this.sentBlockSize.set(Long.parseLong(awesome[1]));
			} catch(Throwable e) {
				LOGGER.error(path + " not a valid block throughput file, dedup stat will be inaccurate");
			}
		} catch(Throwable ioe) {
			LOGGER.error("Error parsing " + path, ioe);
		}
	}
	
	private void storeBlockStats() {
		if(isReadOnly()) {
			return;
		}
		String path = "/mnt/ads/" + siteId + ".thru";
		String awesome = "" + sentBlocks.get() + "," + sentBlockSize.get();
		try (FileWriter fw = new FileWriter(path)) {
			fw.append(awesome);
		} catch(IOException ioe) {
			LOGGER.error("Error saving " + path, ioe);
		}
	}
	
	
	/**
	 * Obtains a local (temporary) path to an ECL
	 * @return satan incarnate
	 */
	public PathedGetResult getEcl(String uuid, long version, DoubleConsumer progress) {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		try {
			if(version * 1000 < new SimpleDateFormat("yyyy").parse("1980").getTime()) {
				throw new CloudException("Timestamp " + version + " is invalid for uuid " + uuid);
			}
		} catch (ParseException e) {
				;
		}
		VmVersion vile = hManager.getMetaData(uuid, version);
		if(vile == null) {
			String timey = DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(version * 1000));
			throw new CloudException("Version " + timey + " (" + version + ") of " + uuid + " not found on site " + siteId);
		}
		return VaultUtil.getBlockViaFile(adp, vile.getVaultId() + ".ecl", progress);
	}
	
	/** 
	 * A cheap trick
	 */
	public CloudAdapter getCloudAdapter() {
		return VaultSettings.instance().getAdapter(siteId);
	}
	
	public void clearPrints(Set<Print> prints) {
		if(hasShutDown) {
			throw new CloudException("ODS is shut down, purge request ignored");
		}
		jManager.applyJournal(new IteratorCursor(prints.iterator(), prints.size()), JournalManager.Action.Clear, (d) -> {;}, "Maint Purge" );
	}
	
	public Pair<Long,Long> refCheck(String ownerInstallId) {
		LOGGER.debug("Beginning refCheck");
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		Pair<Long,Long> returnPair = new Pair<Long,Long>(0L,0L);
		Pair<Long,Long> jRefs = jManager.getRefCounts();
		returnPair.first = jRefs.second;
		
		List<Long> versions = hManager.listAllVersions();
		DoubleConsumer p = new KurganCommander.Dispatch();
		AtomicLong shlong = new AtomicLong();
		List<Exception> errorz = new ArrayList<Exception>();
		VaultUtil.reduce(10, () -> 
			versions.parallelStream().forEach(f -> {
				try {
					String foo = f + ".ecl";
					LOGGER.debug(foo);
					PathedGetResult rez = VaultUtil.getBlockViaFile(adp, foo, p);
					try (InputStream useless = rez.in) {
						EclReader reader = new EclReader(rez.localPath);
						HclCursor curses = reader.createGlobalCursor();
						shlong.addAndGet(curses.count());
					}
				} catch(Exception e) {
					errorz.add(e);
				}
		}));
		if(errorz.size() != 0) {
			throw new CloudException(errorz.get(0));
		}
		returnPair.second = shlong.get();
		
		if(returnPair.first.longValue() != returnPair.second.longValue()) {
			// Break here
			LOGGER.info("offsite Ref check inconsistent...");
		}
		LOGGER.info("Refcheck returning " + returnPair.first + " ods database refs and " + returnPair.second + " restorable journal refs. There are " + jRefs.first + " journal blocks.");
		/*String rezzy = "-1"; 
		try {
			if(siteId == 0) {
				rezzy = VaultUtil.ezExec("/bin/sh -c find /mnt/ads/blocks -type f |wc -l");
			} else {
				rezzy = VaultUtil.ezExec("/bin/sh -c find /mnt/ods1/blocks -type f |wc -l");
			}
		} catch(IOException ioe) { ;} 
		int foo = Integer.parseInt(rezzy);
		if(foo == jRefs.getFirst().longValue()) {
			LOGGER.info("Block count " + foo + " matches blocks on disk");
		} else {
			LOGGER.error("BLOCK COUNT " + jRefs.getFirst()  + " DOES NOT MATCH " + foo + " BLOCKS ON DISK");
		}*/
		
		return returnPair;
	}
	
	/**
	 * Starts a new vault
	 * @param sourceEcl the ECL representing all the vault metadata
	 * @param concurrencyLevel the number of concurrent VaultTx vaulters you want at one time
	 * @param progress progress updates
	 * @return a collection of VaultTx instances used to conduct vaulting I/O
	 * @throws SyncException
	 */
	public synchronized List<VaultTx> makeVault(MetaGarbage garbage, String sourceEcl, BlockSource bs, int concurrencyLevel, DoubleConsumer progress, JobControl control) throws SyncException {

		LOGGER.debug( "Entering makeVault for ECL " + sourceEcl);
		if(needsSync(garbage.installId).first == true) {
			throw new SyncException(needsSync(garbage.installId).second);
		}
		logRefCounts();
		LOGGER.info("Ods starting new commit journal for siteId " + siteId + " for job " + control);
		try {
			nukeSync();
			// Do a cleanup before each vault 
			hManager.reconCleanup(control);
			EclFlags flags = hManager.createTxBegin();
			this.currentLargestTx = flags.getTxNo();
			EclReader reader = new EclReader(sourceEcl);
			// Reduce concurrency for very small vaults
			HclCursor scrappy = reader.createGlobalCursor();
			while(scrappy.count() / concurrencyLevel < 10) {
				concurrencyLevel--;
				if(concurrencyLevel == 1) break;
			}
			VmVersion vv2 = reader.toVmVersion(flags);
			NimbusVersion vv = new NimbusVersion(vv2);
			vv.setJobId(garbage.jobId);
			vv.setVmId(garbage.vmId);
			vv.setSiteId(siteId);
			// This check is very necessary, if a little belated
			if(hManager.versionExists(vv)) {
				throw new DuplicateVaultException("Journal on site " + siteId + " already exists " + vv.toString());
			}
			List<HclWriterUtil> writers = null;
			try {
				writers = createWriters(concurrencyLevel +1, reader.createGlobalCursor().count() * HclReaderUtil.RECORD_SIZE);
			} catch (IOException e) {
				throw new CloudException(e);
			}
			control.control();			
			jManager.createVaultLog(reader.createGlobalCursor(), writers, VaultUtil.nestedProgress(progress, 0, 50));
			//verifyVaultLog(reader.path, new HclReaderUtil(writers.get(0).getPath()).createCursor());
			control.control();
			
			List<VaultTx> returnMe = new ArrayList<VaultTx>();
			int parentTx = -1;
			VaultTx parent = null;
			
		
			HclWriterUtil firstWriter = writers.remove(0);
			bs.load(firstWriter, progress, control);
			
			for(HclWriterUtil w : writers) {
				control.control();
				int localTx = vManager.register(w.getPath(), parentTx, control);
				VaultTx vtx = new VaultTx(localTx, flags.getTxNo(), sourceEcl, vv);
				if(parent == null) {
					parentTx = localTx;
					parent = vtx;
				} else {
					// Assign this transaction's parent
					vtx.parent = parent;
				}
				returnMe.add(vtx);
			}
			
			control.control();
			// Commit the begin tx
			hManager.commitBegin(flags);
			// Put the ECL AFTER so these refs are not applied to the offsite database UNLESS
			// the flags are propigated first
			putEcl(sourceEcl, flags, VaultUtil.nestedProgress(progress, 50, 50), control);
			unnukeSync();
			progress.accept(100);
			return returnMe;
		} catch (CloudException ce) {
			throw ce;
		} catch(Exception e) {
			throw new CloudException(e);
		}
	}
	

	/**
	 * Starts (and completes) a delete vault
	 */
	public synchronized void makeDelete(String ownerInstallId, String uuid, long epoch, DoubleConsumer 
															progress, JobControl control) throws SyncException {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		if(needsSync(ownerInstallId).first == true) {
			throw new SyncException(needsSync(ownerInstallId).second);
		}
		LOGGER.info("Ods starting new delete for siteId " + siteId);
		VmVersion crap = hManager.getMetaData(uuid, epoch);
		if(crap == null) {
			throw new CloudException("Version not found with uuid " + uuid + " and epoch " + epoch);
		}
		VmVersionKey kay = new VmVersionKey(crap);
		if(VersionLocker.instance().wouldWait(kay)) {
			LOGGER.info("Version " + crap + " is locked for munge, delete must wait");
		}
		VersionLocker.instance().waitUntilUnlocked(kay);
		nukeSync();
		long origTx = crap.getVaultId();
		try {
			hManager.reconCleanup(control);
			EclFlags flags = hManager.createTxBegin();
			this.currentLargestTx = flags.getTxNo();
			flags.setDeleteTx(origTx);
			control.control();
			PathedGetResult rez = VaultUtil.getBlockViaFile(adp, origTx + ".ecl", VaultUtil.nestedProgress(progress, 0, 33));
			control.control();
			try (InputStream is = rez.in) {
				EclReader reader = new EclReader(rez.localPath);
				jManager.applyJournal(reader.createGlobalCursor(), JournalManager.Action.Decrement, 
															VaultUtil.nestedProgress(progress, 33, 33), "" + origTx);
				control.control();
				putEcl(rez.localPath, flags, VaultUtil.nestedProgress(progress, 66, 33), control);
			}
			control.control();
			hManager.commitDelete(flags);
			progress.accept(100);
			logRefCounts();
			unnukeSync();
		} catch (IOException e) {
			throw new CloudException(e);
		}
		
	}
		
	public int deleteCount() {
		return jManager.deletePrintCount();
	}
	
	public int maintPurge(String ownerInstallId, DoubleConsumer progress, JobControl control) throws SyncException {
		if(isReadOnly()) {
			return 0;
		}
		List<Long> purgeTxs = null;
		synchronized(this) {
			if(needsSync(ownerInstallId).first == true) {
				LOGGER.info("maintPurge cannot operate at this time--sync not established");
				return -1;
			}
			// 	Obtain list of transactions that are eligable for purge
			purgeTxs = hManager.getPurgeTransactions();
			if(maintRunning) {
				LOGGER.info("Maintenance thread already running for site " + siteId + ", will no-op");
				return -1;
			} else {
				maintRunning = true;
			}
		}
		int currentCount = 0;
		try {
			LOGGER.trace("Beginning maintPurge cycle");
			CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
			Set<Print> conflicts = new HashSet<Print>();
			int totalToDelete = deleteCount();
			boolean hasLoggedOnce = false;
			int maintPurgeBatchSize = VaultSettings.instance().getMaintPurgeBatchSize(siteId);
		
		
			while(jManager.deletePrintCount() - conflicts.size() > 0) {
				control.control();
				// A short backoff to give munge threads a chance
				try { Thread.sleep(5); } catch (InterruptedException e) { ;}
				if(conflicts.size() > maxConflictPrints) {
					LOGGER.info(conflicts.size() + " actively vaulting prints conflict with purge, which exceeds limit."
							+ "Purge is returning early for now.");
					progress.accept(100);
					return -1;
				}
				if(hasLoggedOnce == false) {
					LOGGER.info("maintPurge purged " + currentCount + " of " + totalToDelete + " prints");
					hasLoggedOnce = true;
				} else {
					LOGGER.debug("maintPurge purged " + currentCount + " of " + totalToDelete + " prints");
				}
				
				updatePurgeStats(currentCount, totalToDelete);
				
				// Obtain a VaultManager lock to prevent new vaulters from sneaking in
				Set<Print> toDelete = jManager.getDeletePrints(conflicts, maintPurgeBatchSize);
				try (Closeable c = vManager.getDeleteLock(control, false)) {
					// Determine which prints from this batch are conflicted
					List<Print> currentConflicts = toDelete.parallelStream().filter(p -> {
	 					return vManager.isActivePrint(p) == true;})
	 						.collect(Collectors.toList());
		 			conflicts.addAll(currentConflicts);
					// Delete all other non-conflcted guys				
					toDelete.removeAll(conflicts);
					String msg = "About to purge " + toDelete.size() + " prints";
					if(currentConflicts.size() > 0) {
						msg += ", skipping " + currentConflicts.size() + " conflicts";
					}
					LOGGER.debug( msg);
					VaultUtil.reduce(deleteThreadCount, () -> toDelete.parallelStream().forEach(p -> { 
						try {
							adp.del(p.toString());
						} catch(CloudException ce) {
							LOGGER.error("Nonfatal error deleting block " + p.toString());
						}
					// TODO: come the day when ppl use a seperate Alike process to reconcile, this will be in contention
					}));
					appendToDeleteLog(toDelete);
				} catch(IOException ioe) {
					throw new CloudException(ioe);
				}
				try {
					jManager.finishPurge(toDelete);
					badBlockMan.heal(new ArrayList<Print>(toDelete));
				} catch(Throwable t) {
					// Seems preventative
					nukeSync();
					throw t;
				}
				currentCount += toDelete.size();
				if(currentCount > 0) {
					double ferkface = ((double) currentCount / totalToDelete) * 100.00;
					progress.accept(ferkface);
				}
			} // end while
		// reconcile delete transactions
		// only do this if there were no conflicts.
		// Otherswise, we can try again next time.
		if(conflicts.isEmpty()) {
			synchronized(this) {
				if(needsSync(ownerInstallId).first == false) {
					nukeSync();
					hManager.reconcilePurgeTransactions(purgeTxs);
					LOGGER.info("Purge of site " + siteId + " reconciled the following transactions: " + purgeTxs);
					hManager.reconCleanup(control);
					unnukeSync();
				}
			}
		}
		progress.accept(100);
		LOGGER.info("Completed purge of " + currentCount + " prints from data store " + siteId);
		logRefCounts();
		updatePurgeStats(currentCount, totalToDelete);
		} finally {
			synchronized(this) {
				maintRunning = false;
			}
		}
		return currentCount;
	}

	/**
	 * Returns true is a rebuild was required
	 * @return
	 */
	public synchronized boolean sync(String installId, DoubleConsumer progress, JobControl control) {
		boolean presentlyReadOnly = isReadOnly();
		if(ownerId.equals("") == false) {
			if(installId.equals(ownerId) == false) {
				// Don't bother users with this anymore, as it's useless
				//String msg = "Site " + siteId + " is owned by " + ownerId + ", not by " + installId + ". If this bothers you, delete the file owner.id";
				//LOGGER.info(msg);
			}
		} else {
			LOGGER.info("Datastore " + siteId  + " owner is blank, taking ownership with " + installId);
			setOwner(installId);
		}
		if(needsSync(installId).first == false) {
			LOGGER.debug("Datastore " + siteId + " is in sync.");
			isNuked = false;
			hasEverSynced = true;
			return false;
		}
		
		
		nukeSync();
		LOGGER.warn("Loading datastore " + siteId + "... this may take a moment");
		rebuild(presentlyReadOnly, progress, control);
		if(presentlyReadOnly == false) {
			hManager.postSyncCleanup(control);
			LOGGER.info("Sync established for site " + siteId + ". Serializing sync state.");
			// Put us in full sync
			setType();
			hManager.reconCleanup(control);
			unnukeSync();
			hManager.setTimestamp(System.currentTimeMillis());
		} else {
			LOGGER.info("Initial sync skipped for " + siteId + ", restart will be REQUIRED for write operations"); 
		}
		this.notify();
		//hasGoodTimestamp = true;
		logRefCounts();
		progress.accept(100);
		return true;
	}
	
	private synchronized void setType() {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		byte [] typeString = null; 
		try {
			typeString = ("" + adp.getType().getValue()).getBytes("US-ASCII");
		} catch(Exception e) { ; }
		VaultUtil.putBlockFromMem(typeString, typeString.length, adp, ODS_TYPE_FILE_NAME);
	}
	
	public synchronized void setOwner(String installId) {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		LOGGER.info("Setting ods owner to " + installId);
		try {
			VaultUtil.putBlockFromMem(installId.getBytes("US-ASCII"), installId.length(), adp, "owner.id");
		} catch (UnsupportedEncodingException e) {
			throw new CloudException(e);
		}
		ownerId = installId;
	}
	
	public synchronized String getOwner() {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		if(ownerId.equals("")) {
			if(adp.stat("owner.id") == true) {
				ownerId = VaultUtil.getBlockToString(adp, "owner.id");
				LOGGER.info("Determined datastore owner is " + ownerId);
			} else {
				LOGGER.info("Datastore owner is currently blank");
			}
		}
		return ownerId;
	}
	
	public int getSiteId() {
		return siteId;
	}
	
	public VaultManager getVaultManager() {
		return vManager;
	}
	
	synchronized void unnukeSync() {
		if(isReadOnly() == false) {
			// Sync disks
			try {
				VaultUtil.ezExec(VaultCommandAdapter.CMD_FS_SYNC);
			} catch(IOException ioe) {
				throw new CloudException(ioe);
			}
			// Unt now set sync
			hManager.setTransactMd5(hManager.getRemoteMd5());
		}
		hasEverSynced = true;
		isNuked = false;
		this.notify();
	}
	
	void nukeSync() {
		//LOGGER.debug("Nuking sync for ds " + siteId);
		isNuked = true;
		hManager.setTransactMd5("__UNSYNC");
	}
	
	/*public boolean isSynced() {
		return isNuked;
	}*/
	
	synchronized void commitVault(long txNo, String eclPath, VmVersion version, DoubleConsumer progress) {
		nukeSync();
		// We've already put the ECL, so at this point calling this will make the vault "real"
		hManager.commitVault(txNo, version);
		// Now if we crash before applying the journal, the vault is still "real" 
		// because we are in a nuked state and will force a rebuild
		EclReader reader = new EclReader(eclPath);
		jManager.applyJournal(reader.createGlobalCursor(), JournalManager.Action.Incriment, progress,"" + txNo);
		unnukeSync();
		// Okay NOW the vault is 100% on both ends, continue
		logRefCounts();
		
		
	}
	
	private void updatePurgeStats(int currentCount, int totalToDelete) {
		try (FileWriter fw = new FileWriter("/tmp/metrics/" + siteId + "_purge.stats")) {
			fw.append("" + currentCount + "\n");
			fw.append("" + totalToDelete);
		} catch(IOException ioe) {
			LOGGER.info("Unable to update purge stats", ioe);
		}
	}
	
	private void logRefCounts() {
		if(VaultSettings.instance().isParanoid()) {
			refCheck("");
		} else {
			Pair<Long,Long> nicePair = jManager.getRefCounts();
			LOGGER.info("Data store " + siteId + " contains " + nicePair.first + " prints and " + nicePair.second + " refs");
		}
	}
	
	private void putEcl(String sourceEcl, EclFlags flags, DoubleConsumer progress, JobControl control) throws IOException {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		// Put the ecl to offsite
		String dest = flags.getTxNo() + ".ecl";
		long sz = new File(sourceEcl).length();
		EclReader reader = new EclReader(sourceEcl);
		if(reader.alreadyHasMd5()) {
			sz -= 16;
		}
		
		String oldMd5 = CryptUtil.md5OfFile(sourceEcl, sz); 
		VaultUtil.putBlockFromFile(adp, sourceEcl, sz, dest, progress, control);
		if(VaultSettings.instance().isParanoidEclDisabled(siteId) == false) {
			GetResult rez = adp.getBlock(dest, 0);
			rez.in.close();
			if(oldMd5.equals(rez.md5) == false) {
				throw new CloudException("Put failed, ECL cryptographic key " + rez.md5 + " does not match " + oldMd5 + " for ECL " + dest);
			}
			LOGGER.debug("ECL cryptographic key " + rez.md5 + " verified for ECL " + dest);
			
		}
	}
	
	private Pair<Boolean,String> needsSync(String installId) {
		Pair<Boolean,String> nicePair = new Pair<Boolean,String>(false, "Sync established");
		if(getOwner().equals(installId) == false) {
			LOGGER.debug(installId + " is not datastore owner of site " + siteId + ", proper owner is: " + getOwner());
		}
		if(isNuked == true && isFirstSync == false) {
			LOGGER.info("Site " + siteId + " is unsynced, sync must be re-established");
			nicePair = new Pair<Boolean,String>(true, "Sync lost");
			return nicePair;
		}
		if(hManager.hasGoodMd5() == false && isFirstSync == true) {
			LOGGER.info("MD5 mismatch for ods " + siteId);
			nicePair = new Pair<Boolean,String>(true, "MD5 mismatch");
			return nicePair;
		}
		isFirstSync = false;
		//hasGoodTimestamp = true;
		/* if(hasGoodTimestamp == null) {
			hasGoodTimestamp = hManager.checkTimestamp();
		}
		if(hasGoodTimestamp == false) {
			LOGGER.info("Bad timestamp for siteId " + siteId);
			nicePair = new Pair<Boolean,String>(true, "Bad timestamp");
			return nicePair;
		}*/
		if(VaultSettings.instance().isParanoid()) {
			refCheck(installId);
		}
		return nicePair;
	}
	
	
	
	private List<HclWriterUtil> createWriters(int concurrencyLevel, int eclSize) throws IOException {
		List<HclWriterUtil> returnMe = new ArrayList<HclWriterUtil>();
		for(int x = 0; x < concurrencyLevel; ++x) {
			File phile = File.createTempFile("ncl", "" + x, new File(VaultSettings.instance().getTempPath(eclSize / concurrencyLevel)));
			phile.deleteOnExit();
			returnMe.add(new HclWriterUtil(phile.getPath()));
		}
		return returnMe;
	}
	
	private void resync(DoubleConsumer progress) {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		int count = 0;
		hManager.resync((j, p) -> {
			String myPath = j.getTxNo() + ".ecl";
			// The ECL was never created, which is OK
			if(adp.stat(myPath) == false) {
				LOGGER.debug("ECL for flag " + j + " does not exist, skipping its application");
				return;
			}
			PathedGetResult rez = VaultUtil.getBlockViaFile(adp, myPath);
			try (InputStream is = rez.in) {
				EclReader reader = new EclReader(rez.localPath);
				jManager.applyJournal(reader.createGlobalCursor(), JournalManager.Action.Clear, VaultUtil.nestedProgress(progress, p, 1), "" + j.getTxNo());
			} catch(IOException e) { throw new CloudException(e); };
		}, progress);
		LOGGER.debug("Resync finished after finding " + count + " active vault journals to clear");
	}
	
	public boolean isRebuildDead(String installId) {
		if(needsSync(installId).first == true) {
			if(hasEverSynced == true) {
				return true;
			}
		}
		return false;
	}
	
	private void rebuild(boolean presentlyReadOnly, DoubleConsumer progress, JobControl control) {
		if(hasEverSynced == true) {
			if(this.siteId == 0) {
				// Nuke the "jads" file to help the frontend/scheduler
				try {
					new File("/tmp/jads.status").delete();
				} catch(Throwable t) { ;}
			}
			LOGGER.error("Rebuild on running A2s disabled, rebuild REQUIRED to proceed");
			try {
				Thread.sleep(30 * 1000);
			} catch (InterruptedException e) {
				;
			}
			throw new CloudException("Rebuild rejected, please reboot your A2 after an intermittency");
		}
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		hManager.clear();
		// Nuke it from orbit
		jManager.dropAllData();
		hManager.load(VaultUtil.nestedProgress(progress, 25, 25));
		HeaderEvent he = new HeaderEvent();
		he.type = HeaderEventType.fullSync;
		NimbusDbListener.instance().accept(he);
		NimbusDbListener.instance().flush();
		hManager.reconCleanup(control);
		control.control();
		if(presentlyReadOnly) {
			progress.accept(100.00);
			return;
		}
		hManager.rebuild((f,p) -> { 
			String myPath = f.getTxNo() + ".ecl";
			// The ECL was never created, which is OK
			if(adp.stat(myPath) == false) {
				LOGGER.debug("ECL for flag " + f + " does not exist, skipping its application");
				return;
			}
			PathedGetResult rez = null;
			try {
				rez = VaultUtil.getBlockViaFile(adp, myPath);
			} catch(CloudException ce) {
				LOGGER.error("Rebuild of DataStore " + siteId + "  unable to load metainformation for flag " + f + " with problem: " + ce.getMessage());
				return;
			}
			try (InputStream is = rez.in) {
				EclReader reader = new EclReader(rez.localPath, ECL_REBUILD_CACHE_PRINTS_SIZE);
				// We need to apply journals that are vaulted or active
				JournalManager.Action action;
				if(f.getState() >= TransactState.TransactVaulted.getValue() && f.getDeleteTx() == 0) {
					// A finished vault. Increment refs.
					action = JournalManager.Action.Incriment;
				} else if(f.getState() == TransactState.TransactActive.getValue() || f.getState() == TransactState.TransactRollback.getValue()) {
					// An unfinished or delete vault. Clear it.
					action = JournalManager.Action.Clear;
				} else return;
				jManager.applyJournal(reader.createGlobalCursor(), action, VaultUtil.nestedProgress(progress, p, 1), "" + f.getTxNo());
			} catch(IOException e) { throw new CloudException(e); };
			}, 
			VaultUtil.nestedProgress(progress, 50, 50));
		progress.accept(100.00);
	}
	
	private void appendToDeleteLog(Set<Print> toDelete) {
		if(isReadOnly()) {
			return;
		}
		if(toDelete.isEmpty()) {
			return;
		}
		String path = getPurgeFileName();
		File f = new File(path);
		long skipAmount = f.length() - HclReaderUtil.CODA.length();
		// The phile starts at zero-sized
		if(skipAmount < 0) {
			skipAmount = 0;
		}
		HclWriterUtil writer = new HclWriterUtil(path, skipAmount, false);
		for(Print p : toDelete) {
			writer.writePrint(p);
		}
		writer.writeCoda();
		LOGGER.trace("Wrote "  + toDelete.size() + " prints to purged file");
	}
	
	private String getPurgeFileName() {
		return VaultSettings.instance().getValidatePath() + "/" + siteId +  PURGE_FILE_NAME;
	}
	
}
