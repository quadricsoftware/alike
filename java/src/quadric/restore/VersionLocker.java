package quadric.restore;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.ods.VmVersion;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;

public class VersionLocker {
	private static final Logger LOGGER = LoggerFactory.getLogger( VersionLocker.class.getName() );
	private static final int MAX_STAT_ATTEMPTS = 5;
	private static final int STAT_INTERVAL_MS = 5000;
	private static final int UNLOCK_WAIT_TIMEOUT_SECS = 120;
	private static final int EXTERNAL_APP_CHECK_INTERVAL_MINS = 10;
	private static final String RESERVED_EXTERNAL_FILE_KEY_PREFIX = "__EXT";
	private static final String EXTERNAL_COMMAND_NAME = "/usr/local/sbin/lockedVersions";
	private static VersionLocker me = new VersionLocker();
	
	private Map<String, Integer> failedStats = new HashMap<String, Integer>();
	private Map<VmVersionKey, String> vmStatPaths = new HashMap<VmVersionKey, String>();
	private Stopwatch externalWatcher = new Stopwatch();
	private volatile boolean shouldRun = true;
	private volatile boolean hasStarted = false;
	
	public static VersionLocker instance() {
		return me;
	}
	
	private VersionLocker() {
		LOGGER.debug("VersionLocker starting");
		Thread t = new Thread(() -> {
			int myUsualSleepTime = STAT_INTERVAL_MS;
			while(shouldRun){ 
				Stopwatch watchy = new Stopwatch();
				check();
				if(watchy.getElapsed(TimeUnit.MILLISECONDS) > myUsualSleepTime) {
					LOGGER.info("Version lock stat interval is too short, expanding it");
					myUsualSleepTime *= 2;
				}
				try {
					Thread.sleep(myUsualSleepTime);
				} catch (InterruptedException e) {
					;
				}
			}
		});
		t.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdown();
		}));
	}

	public synchronized void put(VmVersionKey k, String target) {
		vmStatPaths.put(k, target);
		failedStats.put(target, 0);
	}
	
	public void shutdown() {
		shouldRun = false;
	}
	
	
	public synchronized boolean wouldWait(VmVersionKey k) {
		return vmStatPaths.containsKey(k);
	}
	
	public synchronized void waitUntilUnlocked(VmVersionKey k) {
		Stopwatch w = new Stopwatch();
		// Adding a check for first-time runthru 
		// to make sure external lockers are powered up
		while(vmStatPaths.containsKey(k) || hasStarted == false) {
			try {
				this.wait(1000);
			} catch (InterruptedException e) { ; }
			if(w.getElapsed(TimeUnit.SECONDS) > UNLOCK_WAIT_TIMEOUT_SECS) {
				throw new CloudException("Timeout waiting on key " + k + " to become unlocked");
			}
		}
	}
		
	private synchronized void check() {
		//LOGGER.debug("Doing a lock sweep");
		try {
		
			List<String> vile = new ArrayList<String>();
			for(Map.Entry<String,Integer> e : failedStats.entrySet()) {
				if(e.getKey().startsWith(RESERVED_EXTERNAL_FILE_KEY_PREFIX)) {
					// Skip these
					continue;
				}
				if(new File(e.getKey()).exists() == false) {
					if(e.getValue() > MAX_STAT_ATTEMPTS) {
						vile.add(e.getKey());
					} else {
						e.setValue(e.getValue().intValue() +1);
					}
				}
			}
			for(String s : vile) {
				failedStats.remove(s);
				vmStatPaths.entrySet().stream().filter(m -> m.getValue().equals(s)).findFirst().ifPresent(p -> {
					LOGGER.info("Unlocking locked version " + p.getKey().getUuid() 
									+ " with timestamp " + p.getKey().getVersion());
				});
				vmStatPaths.entrySet().removeIf(m -> m.getValue().equals(s));
			}
			// Integration hook for instaboot-locked VMs
			if(!hasStarted || externalWatcher.getElapsed(TimeUnit.MINUTES) > EXTERNAL_APP_CHECK_INTERVAL_MINS) {
				checkExternal();
				externalWatcher.reset();
			}
		} catch(Throwable t) {
			LOGGER.error("Error checking locks", t);
		} finally {
			hasStarted = true;
			this.notify();
		}
	}
	
	private void checkExternal() {
		Set<String> goodPaths = new HashSet<String>();
		try {
			String rez = VaultUtil.ezExec(EXTERNAL_COMMAND_NAME);
			// Only proceed if non-empty
			if(rez.trim().startsWith("EMPTY") == false) {
				//LOGGER.debug("Rez is rez" + rez);
				String [] lines = rez.split("\\s+");
				for(String li : lines) {
					li = li.trim();
					//LOGGER.debug("Line is " + li);
					if(li.equals("")) continue;
					String [] parts = li.split(":");
					//LOGGER.debug("Parts are $parts[0] ");
					VmVersionKey k = new VmVersionKey();
					k.setSiteId(Integer.parseInt(parts[0].trim()));
					String idiot = parts[1].trim();
					// Clean up input if needed
					String u2 = idiot.replace("-", "");
					idiot = u2.toUpperCase();
					k.setUuid(idiot);
					k.setVersion(Long.parseLong(parts[2].trim()));
					String path = RESERVED_EXTERNAL_FILE_KEY_PREFIX + li;
					//LOGGER.debug("I made a path is " + path);
					if(vmStatPaths.containsKey(k) == false) {
						LOGGER.info("Locking external version " + k.getUuid() + " with timestamp " + k.getVersion());
						vmStatPaths.put(k, path);
					}
					goodPaths.add(path);
				}
				if(goodPaths.isEmpty()) {
					LOGGER.error("Empty (invalid) file passed, ignoring");
					return;
				}
			} 
			
		} catch(Throwable t) {
			// If we die, don't do any unlocking, as that would be dangerous as hell
			LOGGER.error("Check of external version locker failed", t);
			return;
		}
		List<String> vile = new ArrayList<String>();
		for(Map.Entry<String,Integer> e : failedStats.entrySet()) {
			if(e.getKey().startsWith(RESERVED_EXTERNAL_FILE_KEY_PREFIX) == false) {
				// Skip these
				continue;
			}
			if(goodPaths.contains(e.getKey()) == false) {
				if(e.getValue() > MAX_STAT_ATTEMPTS) {
					vile.add(e.getKey());
				} else {
					LOGGER.debug("External version lock with key " + e.getKey() 
						+ " declined to renew lock " + (e.getValue() +1) 
						+ "times, will be unlocked after " + MAX_STAT_ATTEMPTS);
					e.setValue(e.getValue().intValue() +1);
				}
			}
		}
		// Cleanup on aisle 5
		for(String s : vile) {
			failedStats.remove(s);
			vmStatPaths.entrySet().stream().filter(m -> m.getValue().equals(s)).findFirst().ifPresent(p -> {
				LOGGER.info("Unlocking externally locked version " + p.getKey().getUuid() 
								+ " with timestamp " + p.getKey().getVersion());
			});
			vmStatPaths.entrySet().removeIf(m -> m.getValue().equals(s));
		}
	}
}
