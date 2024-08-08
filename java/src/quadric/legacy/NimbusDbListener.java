package quadric.legacy;

import java.io.Closeable;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.ods.HeaderManager;
import quadric.ods.HeaderManager.HeaderEvent;
import quadric.ods.VmVersion;
import quadric.ods.dao.Crud;
import quadric.ods.dao.Dao;
import quadric.ods.dao.DaoFactory;
import quadric.ods.dao.VmVersionCrud;

/**
 * Gathers change events and periodically pukes them out to the nimbus.db
 * 
 *
 */
public class NimbusDbListener implements Consumer<HeaderManager.HeaderEvent> {
	private static final Logger LOGGER = LoggerFactory.getLogger( NimbusDbListener.class.getName() );
	
	/**
	 * The maximum number of change events that can be batched before a flush is forced
	 */
	public static final int COALESCE_THRESHOLD_BATCH = 1000;
	
	/**
	 * The maximum ms that can pass after a change event is received before it is flushed
	 */
	public static final int COALESCE_THRESHOLD_MS = 500;
	
	private static NimbusDbListener me = new NimbusDbListener();
	
	private volatile long lastTime = 0;
	private Dao dao;
	private Queue<HeaderManager.HeaderEvent> events = new ArrayDeque<HeaderManager.HeaderEvent>();
	private ReentrantLock lock = new ReentrantLock();
	private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
	//private HeaderManager daddy;
	
	
	
	public static NimbusDbListener instance() {
		return me;
	}
	
	private NimbusDbListener() {
		//this.daddy = daddy;
		String awesome = VaultSettings.instance().getLocalDbPath();
		dao = DaoFactory.instance().create(awesome + File.separator + "nimbusdb.db");
		// Attach cache
		String cachePath = VaultSettings.instance().getRemoteDbPath() + File.separator + "cache.db";
		dao.attach(cachePath);
	}
	
	/**
	 * Forces all batched events to be flushed to the NimbusDB
	 */
	public void flush() {
		try {
			executor.submit(() -> processAll()).get();
		} catch (InterruptedException e) {
			LOGGER.error("Force flush failed, thread was interrupted");
		} catch (ExecutionException e) {
			throw new CloudException(e);
		}
	}
	
	@Override
	public void accept(HeaderEvent t) {
		long addedToQueueTime; 
		try {
			lock.lock();
			events.add(t);
			addedToQueueTime = System.currentTimeMillis();
			if(events.size() > COALESCE_THRESHOLD_BATCH) {
				LOGGER.debug( "Coalesce batch threshold reached; forcing flush");
				lock.unlock();
				executor.submit(() -> processAll());
			} else {
				lock.unlock();
				executor.schedule(() -> { 
					Thread.currentThread().setName("NimbusDbListener");
					doProcessAllIfNeeded(addedToQueueTime);
				},
				COALESCE_THRESHOLD_MS, TimeUnit.MILLISECONDS);
			}
				
		} finally {
			if(lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
		
	}
	
	private void doProcessAllIfNeeded(long addedToQueueTime) {
		if(addedToQueueTime <= lastTime) {
			// All good
			return;
		}
		LOGGER.trace("Coalesce threshold time passed; forcing flush");
		processAll();
	}

	
	
	private void processAll() {
		try {
			Crud.executeWithRetryReal(() -> {
				try {
					doProcessAll();
				} catch(SQLException sqle) {
					throw sqle;
				} catch(Throwable t) {
					LOGGER.error("Error saving to NimbusDb", t);
				}
			}, "NimbusDb");
		} catch(Throwable t) { 
			LOGGER.error("Error saving to NimbusDb", t);
		}
	}
	
	private void doProcessAll() throws Exception {
		long t1 = System.nanoTime();
		// We need to lock HC's database to prevent contention
		String pathy = VaultSettings.instance().getRemoteDbPath() + "/cache.db";
		try (Closeable locker = DaoFactory.instance().create(pathy).lockIt()) {
			try (Connection con = dao.getWriteConnection()) {
				int sz = 0; 
				try {
					lock.lock();
					lastTime = System.currentTimeMillis();
					sz = events.size();
					if(sz == 0) {
						// Nothing to see here, folks
						return;
					}
					while(events.isEmpty() == false) {
						HeaderManager.HeaderEvent e = events.remove();
						lock.unlock();
						processHeaderEvent(con, e);
						lock.lock();
					}
				} finally {
					if(lock.isHeldByCurrentThread()) {
						lock.unlock();
					}
				}
				cleanupOrphans(con);
				con.commit();
				long t2 = System.nanoTime();
				double awesome = ((double) t2 - t1) / (1000000.00D);
				LOGGER.debug( "Flushed " + sz + " changed items to nimbusDB in " + String.format("%.2f", awesome) + "ms");
			}
		}
	}
	
	
	
	private void processHeaderEvent(Connection con, HeaderEvent t) throws Exception {
		NimbusVersionCrud crud = new NimbusVersionCrud(con);
		NimbusSiteCrud siteCrud = new NimbusSiteCrud(con);
		NimbusVmCrud vmCrud = new NimbusVmCrud(con);
		if(t.type == HeaderManager.HeaderEventType.fullSync) {
			fullSync(con);
			return;
		}
		// These guys are weird...we are just updating stats for a version
		if(t.type == HeaderManager.HeaderEventType.updatePostDedupSize) {
			crud.updateVmPostDedupSize((NimbusVersion) t.version, t.diskNum, t.changeSize);
			return;
		} else if(t.type == HeaderManager.HeaderEventType.updatePreDedupSize) {
			crud.updateVmDeltaSize((NimbusVersion) t.version, t.diskNum, t.changeSize);
			return;
		} else if(t.type == HeaderManager.HeaderEventType.updateFullSize) {
			crud.updateVmFullSize((NimbusVersion) t.version, t.diskNum, t.changeSize);
			return;
		} else if(t.type == HeaderManager.HeaderEventType.maxVersionsChange) {
			vmCrud.updateMaxVersions((NimbusVersion) t.version, t.diskNum);
			return;
		} else if(t.type == HeaderManager.HeaderEventType.updateJobOrig) {
			crud.updateJobOriginalSize(t.diskNum, t.changeSize);
			return;
		} else if(t.type == HeaderManager.HeaderEventType.updateJobDelta) {
			crud.updateJobDelta(t.diskNum, t.changeSize);
			return;
		}
		
		// Ok this stuff all is adding/removing db entries
			
		NimbusVersion nb;
		NimbusSite ns = new NimbusSite();
		try {
			nb = (NimbusVersion) t.version;
		} catch(ClassCastException cce) {
			LOGGER.trace("Resycing VM from non-job origin");
			// This VM version originates offsite, not from a job being run here.
			// This means we won't ever know the jobId that created it, but whatev.
			nb = new NimbusVersion(t.version);
			ResultSet rs = vmCrud.buildQuery("SELECT vmid FROM vm WHERE uuid=?", nb.getPlatformStyleUuid());
			if(rs.next()) {
				LOGGER.trace("VM entry exists in nimbus");
				// This VM already exists. We need its vmid to proceed
				nb.setVmId(rs.getLong(1));
			} else {
				if(t.type == HeaderManager.HeaderEventType.deleted) {
					LOGGER.error("Attempt to delete version of VM that is unknown to nimbusDB: " + t.version.getPlatformStyleUuid());
					return;
				}
				// Oh snap. Need to make this VM.
				LOGGER.info("Nimbus VM table record for vm " + nb.getPlatformStyleUuid() + " does not yet exist, creating it");
				try {
					vmCrud.create(nb);
				} catch(CloudException ce) {
					if(ce.getCause() instanceof SQLiteException) {
						SQLiteException sqle = (SQLiteException) ce.getCause();
						if(sqle.getMessage().contains("SQLITE_CONSTRAINT_UNIQUE")) {
							LOGGER.error("VM " + nb.getNormalizedUuid() + " unexpectedly exists in nimbusDB, will be skipped!");
						}
					}
					
				}
				// Obtain the VM id from the db
				nb.setVmId(vmCrud.getLastInsertId());
			} 
		}
		ns.setVmId(nb.getVmId());
		ns.setSiteId(nb.getSiteId());
		ns.setVersion(nb.getVersion());
		// Now deal with the "version" component of the problem
		switch(t.type) {
		case changed:
			if(crud.querySingle(crud.buildQuery("SELECT * FROM VM_version WHERE vmid=? AND timestamp=?", 
																	nb.getVmId(), nb.getVersion())) == null) {
				// We never actually need to modify a version this way after it's created
				crud.create(nb);
			}
			// Add the site entry
			try {
				siteCrud.create(ns);
			} catch(CloudException ce) {
				if(ce.getMessage().contains("UNIQUE") == false) {
					throw ce;
				}
				LOGGER.debug("Suppressing silly contraint violation on siteId for " + ns);
			}
			break;
		case deleted:
			siteCrud.delete(ns);
			break;
		default:
			throw new CloudException("Not implemented");
		}
	}
	
	/**
	 * Attaches the nimbusDb and diffs out all versions
	 */
	private synchronized void fullSync(Connection con) throws Exception {
		long t1 = System.nanoTime();
		LOGGER.info("Performing full update of NimbusDB.db with datastore version entries");
		
		
		List<HeaderManager.HeaderEvent> myList = new ArrayList<HeaderManager.HeaderEvent>();
		
		
		VmVersionCrud odsCrud = new VmVersionCrud(con);
		NimbusVersionCrud crud = new NimbusVersionCrud(con);
			
		//con.setAutoCommit(true);
		LOGGER.trace("Attached nimbusDB successfully?");
		//con.setAutoCommit(false);
		crud.buildQuery("DELETE FROM both_tables");
		crud.buildQuery("INSERT INTO both_tables SELECT vvv.flagId, vvv.uuid, vvv.version, vvv.siteid from att.vversion_vaulted_v vvv, "
				+ "(select v.uuid, vv.timestamp, vs.siteid "
				+ "FROM vm v, vm_version vv, version_site vs WHERE v.vmid = vv.vmid AND v.vmid = vs.vmid AND vv.timestamp = vs.timestamp) subby "
				+ "WHERE vvv.uuid = subby.uuid and vvv.version = subby.timestamp and vvv.siteid = subby.siteid");
		
		// Determine missing versions
		ResultSet rs = crud.buildQuery("SELECT vv.* FROM vversion_vaulted_v vv WHERE NOT EXISTS "
					+ "(SELECT flagId, siteId FROM both_tables bt WHERE bt.flagId = vv.flagId AND bt.siteId = vv.siteId)");
		// ...and their disks
		ResultSet rs2 = crud.buildQuery("SELECT vd.* FROM vversion_disk_vaulted_v vd WHERE NOT EXISTS "
				+ "(SELECT * FROM both_tables bt WHERE bt.flagId = vd.versionId AND bt.siteId = vd.siteId) ");
		rs2.next();
			
		// Fire all your guns at once, explode into space
		while(rs.next()) {
			VmVersion version = odsCrud.toObject(rs, rs2);
			HeaderManager.HeaderEvent he = new HeaderManager.HeaderEvent();
			he.type = HeaderManager.HeaderEventType.changed;
			he.version = version;
			LOGGER.debug( "Found " + version.toString() + " that needs updating");
			LOGGER.trace("Its metadata is " + version.getMetaData());
			myList.add(he);
		}
		// Delete all nimbus records that are useless
		crud.buildQuery("DELETE FROM version_site WHERE rowid NOT IN "
				+ "(SELECT vs.rowid FROM version_site vs, both_tables bt, vm v "
						+ "WHERE 1=1 "
						+ "AND vs.vmid=v.vmid "
						+ "AND v.uuid = bt.uuid AND vs.siteid = bt.siteid "
						+ "AND version_site.timestamp=bt.timestamp"
					+ ")");
		// Can't do this in finally?
		crud.buildQuery("DELETE FROM both_tables");
		long t2 = System.nanoTime();
		double awesome = ((double) t2 - t1) / (1000000.00D);
		LOGGER.info("Found " + myList.size() + " new or deleted versions in " + String.format("%.2f", awesome) + "ms. About to process them.");
		events.addAll(myList);
		
	}
	
	/**
	 * Clean up orphaned vm_versions and vm_files
	 * @param con
	 * @throws Exception
	 */
	private void cleanupOrphans(Connection con) throws Exception {
		NimbusVersionCrud crud = new NimbusVersionCrud(con);
		crud.buildQuery("DELETE FROM vm_version WHERE rowid NOT IN (SELECT vv.rowid FROM vm_version vv, version_site vs "
							+ "WHERE vv.vmid = vs.vmid AND vv.timestamp = vs.timestamp)");
		crud.buildQuery("DELETE FROM vm_files WHERE rowid NOT IN (SELECT vf.rowid FROM vm_files vf, vm_version vv "
							+ "WHERE vf.vmversion = vv.vmversion AND vv.vmid = vf.vmid)");
	}

}
