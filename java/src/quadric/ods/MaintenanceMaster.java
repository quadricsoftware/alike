package quadric.ods;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.DataStores;
import quadric.blockvaulter.VaultSettings;
import quadric.util.JobCancelException;
import quadric.util.JobControl;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;

@SuppressWarnings("rawtypes")
public class MaintenanceMaster {
	private static final Logger LOGGER = LoggerFactory.getLogger( MaintenanceMaster.class.getName() );
	private static final int MAINT_JOB_HOURS_FULL = 6;
	private static final int MAINT_JOB_HOURS_PURGE = 2;
	private static final int GC_INTERVAL_SECS = 1000;
	
	
	private static MaintenanceMaster me = new MaintenanceMaster();
	private Map<Integer, MaintTracker> maints = Collections.synchronizedMap(new HashMap<Integer,MaintTracker>());
	private ExecutorService killer = Executors.newCachedThreadPool();
	
	private volatile int globalForceOnce = -1;
	private volatile boolean shouldRun = true;
	
	private MaintenanceMaster() { 
		
		
		killer.execute( () -> {
			monitor();
		});
		
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdown();
		}));
		
	}
	
	public static MaintenanceMaster instance() {
		return me;
	}
	
	public synchronized void shutdown() {
		LOGGER.info("Shutdown commencing");
		shouldRun = false;
		for(MaintTracker tracky : maints.values()) {
			tracky.maintJobControl.cancel();
		}
	}

	
	public void force(int siteId) {
		register(this, siteId);
		globalForceOnce = siteId;
		synchronized(this) {
			while(globalForceOnce != -1) {
				try {
					this.wait(1000);
				} catch (InterruptedException e) {; }
			}
		}
	}
	
	public synchronized void register(Object interest, int siteId) {
		if(maints.get(siteId) == null) {
			synchronized(this) {
				if(maints.get(siteId) == null) {
					maints.put(siteId, new MaintTracker(siteId));
				}
			}
		}
		
		maints.get(siteId).refCount.put(interest, null);
		
	}
	
	
	
	private void monitor() {
		Stopwatch gcWatch = new Stopwatch();
		while(shouldRun) {
			try {
				Thread.sleep(10 * 1000);
			} catch (InterruptedException e) {;}
			boolean vile = VaultSettings.instance().isForceMaintenance();
			int x = -1;
			boolean hasAnyActive = false;
			synchronized(this) {
				for(MaintTracker tracky : maints.values()) {
					++x;
					boolean shouldToggleOn = tracky.refCount.size() != 0;
					if(vile && x == 0) {
						shouldToggleOn = true;
					}
					if(globalForceOnce == x) {
						shouldToggleOn = true;
					}
					if(shouldToggleOn == false) {
						if(tracky.maintJobControl.togglePause(true)) {
							LOGGER.info("Toggling OFF maintenance job for site " + x);
						}
					} else {
						killer.execute( () -> runMaint(tracky));
						if(tracky.maintJobControl.togglePause(false)) {
							LOGGER.trace("Toggling ON maintenance job for site " + x);
							// Give it a kick in the nards in case this is the first time
							
						}
						hasAnyActive = true;
					}
				}
			}
			if(hasAnyActive == false) {
				if(gcWatch.getElapsed(TimeUnit.SECONDS) > GC_INTERVAL_SECS) {
					gcWatch.reset();
				} else {
					continue;
				}
				Stopwatch watchy = new Stopwatch();
				long before = Runtime.getRuntime().freeMemory();
				System.gc();
				long after = Runtime.getRuntime().freeMemory();
				long diff = ((after - before) / 1024);
				if(diff < 0) diff = 0;
				LOGGER.debug("Garbage collection reclaimed " + diff + "KB of RAM in " + watchy.getElapsed(TimeUnit.MILLISECONDS) + "ms");
				
			}
		}
		
	}
	
	private void dummy() {
		/* long diff = System.currentTimeMillis() - stats.getLastPurge();
		boolean alwaysRunMaintenance = VaultSettings.instance().isForceMaintenance();
		
		boolean runReconcile = true;
		if(alwaysRunMaintenance == false) {
			if(diff / 1000 / 60 / 60 < MAINT_JOB_HOURS_PURGE) {
				return;
			}
			runReconcile = false;
			diff = System.currentTimeMillis() - stats.getLastRecon();
			if(diff / 1000 / 60 / 60 >= MAINT_JOB_HOURS_FULL) {
				runReconcile = true;
			}
		} */
	}
	
	private void runMaint(MaintTracker main) {
		Ods ds = null;
		synchronized(main) {
			if(main.isRunning == true) {
				return;
			}
			ds = DataStores.instance().getOds(main.dsNum);
			if(ds.isReadOnly()) {
				return;
			}
			long diff = ds.getCurrentLargestTx() - main.largestTx; 
			if(globalForceOnce != main.dsNum) {
				if(diff < VaultSettings.instance().getReconFreq(main.dsNum)) {
					LOGGER.trace("No maintenance needed at this time");
					//maintJobControl.togglePause(true);
					return;
				}
			}
		}
		JobControl maintJobControl = main.maintJobControl;
		main.reset();
		final MaintTracker taint = main;
		DoubleConsumer konsume = (d) -> updateMaintanceProgress(d, taint);
		final MaintStats stats = ds.revealHeaderManager().getMaintStats();
		try {
			main.isRunning = true;
			Stopwatch timer = new Stopwatch();
			LOGGER.info("Starting maintenance job");
			
			DoubleConsumer nested1 = VaultUtil.nestedProgress(konsume, 50, 50);
			DoubleConsumer nested2 = VaultUtil.nestedProgress(konsume, 50, 50);
			//ds.reconcile(nested1, maintJobControl);
			stats.setLastRecon(System.currentTimeMillis());
			stats.setReconTime(timer.getElapsed(TimeUnit.MILLISECONDS));
			maintJobControl.control();
			ds.maintPurge(ds.getOwner(), nested2, maintJobControl);
			
			stats.setLastPurge(System.currentTimeMillis());
			stats.setPurgeTime(timer.getElapsed(TimeUnit.MILLISECONDS));
			maintJobControl.control();
			LOGGER.info("Maintenance job completed in " + timer.getElapsed(TimeUnit.MINUTES) + " minutes");
			ds.revealHeaderManager().setMaintStats(stats);
			// Pause ourselves
			//maintJobControl.togglePause(true);
		} catch(JobCancelException jce) {
			LOGGER.debug("Maintenance job ended due to cancelation");
		} catch(Throwable t) {
			LOGGER.error("Maintenance job failed", t);
		} finally {
			taint.largestTx = ds.getCurrentLargestTx();
			taint.isRunning = false;
			// Resolve "force"
			if(globalForceOnce == main.dsNum) {
				synchronized(this) {
					globalForceOnce = -1;
					this.notify();
				}
			}
		}
	}
	
	private static void updateMaintanceProgress(double d, MaintTracker status) {
		int current = (int) d;
		if(current >= status.highestProgress + 10) {
			status.highestProgress = current;
			LOGGER.info("Maintenance for data store " + current + "% complete");
		}
		
	}

}


/**
 * Used to track maintenance job progress etc
 *
 */
class MaintTracker {
	MaintTracker(int siteId) {
		dsNum = siteId;
		reset();
	}
	
	WeakHashMap refCount = new WeakHashMap();
	JobControl maintJobControl = new JobControl();
	int dsNum;
	volatile boolean isRunning = false;
	double highestProgress;
	volatile long largestTx = -1;
	
	void reset() {
		highestProgress = -10;
		// Start as paused
		maintJobControl.togglePause(true);
	}
}
