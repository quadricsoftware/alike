package quadric.ods;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.DataStores;
import quadric.blockvaulter.VaultSettings;
import quadric.fuse.AmbPath;
import quadric.util.HclCursor;
import quadric.util.JobControl;
import quadric.util.Print;
import quadric.util.Stopwatch;


/**
 * Manages concurrent vaulters via a cache of binary search files to prevent vaulters from resending
 * prints from other active vaults.
 *
 */
public class VaultManager {
	static class Member {
		Member() { ; }
		HclCursor cursor;
		BinarySearchFile searchFile;
		int parent;
		
		public String toString() {
			return "Parent: " + parent;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger( VaultManager.class.getName() );
	private static final int LOCK_TIME_WARNING_SECS = 60; 
	private static final int LOCK_TIME_FATAL_SECS = 60 * 10;
	
	private Map<Integer,String> reverseAmbMembers = new HashMap<Integer,String>();
	private Map<String,Integer> ambMembers = new HashMap<String,Integer>();
	private Map<Integer,Long> leases = new HashMap<Integer,Long>();
	private Map<Integer,Member> members = new HashMap<Integer, Member>();
	private Map<Integer,Object> associates = new HashMap<Integer, Object>();
	//private Map<String,List<AmbMember>> ambMembers = new HashMap<String,List<AmbMember>>();
	private DogCrap dogCrap = new DogCrap();
	//private Set<Print> actives = new HashSet<Print>();
	private int myKey = 0;
	private Object deleteLock = new Object();
	private ReentrantLock unregisterLock = new ReentrantLock();
	private boolean isDeleteWriteLocked = false;
	private boolean isDeleteReadLocked = false;
	private ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(1);
	private volatile boolean shutdownNow = false;
	private int siteId;
	private ReentrantLock globoLock = new ReentrantLock();
	private String lockOwner = "nobody";
	
	
	
	public VaultManager(int siteId) {
		this.siteId = siteId;
		long maxMillis = VaultSettings.instance().getVaultLeaseAgeMaxMs();
		LOGGER.debug("VaultManager initting for siteId " + siteId + ", commit timeout is: " + maxMillis);
		threadPool.scheduleAtFixedRate(() -> {
			Thread.currentThread().setName("VaultManagerIdleRegisters");
			checkForIdleRegisters();
		}, 10, 60, TimeUnit.SECONDS);
		
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdown();
		}));
	}
	
	public void shutdown() {
		if(shutdownNow == true) {
			return;
		}
		shutdownNow = true;
		checkForIdleRegisters();
		threadPool.shutdownNow();
	}
	
	public int register(String orderedDifFile, int parent, JobControl control) {
		return register(orderedDifFile, parent, 0, control);
	}
	
	
	/**
	 * Registers a ResumeFile to accelerate a resumed vault based on stuff already sent
	 * @param file
	 * @param control
	 * @return
	 */
	public int register(ResumeFile file, JobControl control, int siteId) {
		MaintenanceMaster.instance().register(control, siteId);
		int myTx = register(file.getFileName(), -1, ResumeFile.HEADER_SIZE, control);
		Member m;
		tryLock("Register for resume file " + file.getFileName());
		try {
			m = members.get(myTx);
		} finally {
			globoLock.unlock();
		}
		// Advance to end of cursor
		while(m.cursor.hasNext()) {
			m.cursor.next();
		}
		return myTx;
	}
	 
	
	public int registerMunge(String path, Object misc) {
		tryLock("Register for munge " + path);
		try {
			int tx = -1;		
			tx = ambMembers.getOrDefault(path, -1);
			if(tx == -1) {
				LOGGER.info("Registering new AMB transaction at " + path);
				tx = register(null, -1, 0, null);
				reverseAmbMembers.put(tx, path);
				ambMembers.put(path, tx);
				associates.put(tx, misc);
			}
			MaintenanceMaster.instance().register(path, 0);
			return tx;
		} finally {
			globoLock.unlock();
		}
		
	}
	
	public Map<String,Long> listActiveMunges() {
		Map<String,Long> toReturn = new TreeMap<String,Long>();
		tryLock("listActiveMunges");
		try {
			for(Map.Entry<String,Integer> e : ambMembers.entrySet()) {
				Long lease = leases.get(e.getValue());
				toReturn.put(e.getKey(), lease);
			}
		} finally {
			globoLock.unlock();
		}
		return toReturn;
	}
	
	public Object unregisterMunge(MetaGarbage garbage) {
		String path = AmbPath.makeAmbPath((int) garbage.jobId, (int) garbage.vmId);
		int tx = -1;
		Object ref = null;
		tryLock("unregisterMunge for " + garbage);
		try {
			tx = ambMembers.getOrDefault(path, -1);
			ref = associates.get(tx);
		} finally {
			globoLock.unlock();
		} 
		if(tx != -1) {
			if(ref == null) {
				LOGGER.error("Graceful unregister of munge requested, but no stats found for it! " + garbage);
			} else {
				LOGGER.info("Graceful unregister of AMB munge at path " + path);
			}
			unregister(tx);
		} else {
			LOGGER.error("Can't unregister AMB munge at path " + path);
		}
		return ref;
	}
	
	/**
	 * Registers with the VaultManager. May block if the deleteLock is out.
	 * @param vmSourceData all or a portion of the vault data from the ECL
	 * @param parent -1 if has no parent, otherwise the tx parent
	 */
	public int register(String orderedDifFile, int parent, int skipBytes, JobControl control) {
		MaintenanceMaster.instance().register(control, siteId);
		Member m = new Member();
		if(orderedDifFile != null) {
			try {
				LOGGER.debug("Registering binary search file for site " + siteId);
				m.searchFile = new BinarySearchFile(orderedDifFile, skipBytes);
			} catch(Exception e) {
				throw new CloudException(e);
			}
			m.cursor = m.searchFile.createCursor();
		}
		m.parent = parent;
		try(Closeable c = getDeleteLock(null, true)) {
			tryLock("Register of orderedDifFile " + orderedDifFile);
			try {
				myKey++;
				members.put(myKey, m);
			} finally {
				globoLock.unlock();
			}
			LOGGER.debug("Registering transaction " + myKey + " for site " + siteId + ". Total registered is now " + members.size());
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		
		return myKey;
	}
	
	/**
	 * Obtains a deleteLock which will prevent anyone else from registering a new vault.
	 * @return a resource you must close to relinquish the lock
	 */
	public Closeable getDeleteLock(JobControl control, boolean readOnly) {
		long t1 = System.currentTimeMillis();
		boolean hasWarned = false;
		synchronized(deleteLock) {
			while( ((isDeleteWriteLocked == true || isDeleteReadLocked == true) && readOnly == false)
					|| (isDeleteWriteLocked == true && readOnly == true)
					) {
				try {
					deleteLock.wait(5000);
					if(control != null) {
						control.control();
					}
				} catch(InterruptedException e) { ;}
				long elapsed  = System.currentTimeMillis() - t1;
				if(elapsed / 1000 > LOCK_TIME_WARNING_SECS && hasWarned == false) {
					LOGGER.warn("DeleteLock is taking a long time to obtain, writer locked is " + isDeleteWriteLocked);
					hasWarned = true;
				}
				if(elapsed /1000 > LOCK_TIME_FATAL_SECS) {
					throw new CloudException("Lock timeout of " + LOCK_TIME_FATAL_SECS + " exceeded for delete lock");
				}
			} // end while
			if(readOnly == false) {
				isDeleteWriteLocked = true;
			} else {
				isDeleteReadLocked = true;
			}
		}
		return new Closeable() {

			@Override
			public void close() throws IOException {
				LOGGER.trace("Closing");
				synchronized(deleteLock) {
					if(readOnly) {
						isDeleteReadLocked = false;
					} else {
						isDeleteWriteLocked = false;
					}
					deleteLock.notifyAll();
				}
			}
		};
	}
	
	
	/**
	 * Unregister with the VaultManager when you are done or aborted
	 * @param txNo what transact.tx txNo you are done with
	 * @return true if there are related batch transactions still registered, false if this is the last of its batch
	 */
	public boolean unregister(int txNo) {
		tryLock("Unregister " + txNo);
		try {
					
			String ambFilePart = reverseAmbMembers.get(txNo);
			if(ambFilePart != null) {
				LOGGER.debug( "Found AMB part to unregister. It is associated with " + ambFilePart);
				reverseAmbMembers.remove(txNo);
				ambMembers.remove(ambFilePart);
				associates.remove(txNo);
			}
			leases.remove(txNo);
			Member m = members.remove(txNo);
			dogCrap.remove(txNo);
			if(m == null) {
				//LOGGER.debug( "Attempt to unregister a transaction that was never initially registered");
				return false;
			}
			LOGGER.debug( "Unregistering transaction " + txNo + ". Total registered is now " + members.size());
			if(m.searchFile != null) {
				unregisterLock.lock();
				try {
					// Pass the unregister lock to avoid complications
					m.searchFile.delete();
				} finally {
					unregisterLock.unlock();
				}
			}
			boolean found = members.entrySet().stream().anyMatch(map -> 
							map.getKey() == m.parent 										// My parent
							|| map.getValue().parent == txNo 								// My child
							|| map.getValue().parent == m.parent && m.parent != -1			// My sibling
							);
			LOGGER.debug( "Unregistered transaction " + txNo + "; is it part of an outstanding batch? " + found);
			return found;
		} finally {
			globoLock.unlock();
		}
			
	}
	
	public HclCursor revealCursor(int txNo) {
		tryLock("revealCursor " + txNo);
		try {
			return members.get(txNo).cursor;
		} finally {
			globoLock.unlock();
		}
	}
	
	/**
	 * Tells the vault manager that you are done with the previous print
	 * and want to process the next one in your sequence
	 * @return next print to send or null if nothing more to do
	 */
	public Print vaultNext(int txNo) {
		Member m ;
		tryLock("vaultNext " + txNo);
		try {
			m = members.get(txNo);
		} finally {
			globoLock.unlock();
		}
		if(m == null || m.cursor == null) {
			// Break here
			throw new CloudException("VaultManager invalid state due to null cursor or missing register");
		}
		if(m.cursor.hasNext() == false) {
			return null;
		}
		Print p = m.cursor.next();
		int checkTx = m.parent;
		if(checkTx == -1) {
			// We are the daddy
			checkTx = txNo;
		}
		lockPrint(txNo, p, null);
		return p;
		
	}
	
	public Object lockPrint(int txNo, Print p, JobControl control) {
		try(Closeable c = getDeleteLock(null, true)) {
			Object coolGuy = null;
			tryLock("lockPrint " + txNo);
			try {
				dogCrap.put(txNo, p);
				leases.put(txNo, System.currentTimeMillis());
				coolGuy = associates.get(txNo);
			} finally {
				globoLock.unlock();
			}
			return coolGuy;
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		
	}
	
	
	/**
	 * Checks to see if a print is currently being vaulted
	 * @param p the print to check
	 * @return true if it's being vaulted
	 */
	public boolean isActivePrint(Print p) {
		tryLock("isActivePrint " + p);
		try {
			return dogCrap.active(p);
		} finally {
			globoLock.unlock();
		}
	}
	
	/**
	 * Forcably unregister ALL munges for this datastore
	 */
	public void forceUnregisterAll() {
		tryLock("forceUnregisterAll");
		try {
			List<Integer> affected = new ArrayList<Integer>();
			for(Map.Entry<Integer,Long> e : leases.entrySet()) {
				affected.add(e.getKey());
			}
			// Avoid modifying the very collection we're working on 
			for(int i : affected) {
				LOGGER.warn("Forcing cleanup for vault transaction " + i);
				// Need to undo this guy
				purge(i);
				unregister(i);
			}
		} finally {
			globoLock.unlock();
		} 
	}
	
	
	private void tryLock(String lockOwner) {
		Stopwatch watchy = new Stopwatch();
		boolean ok = false;
		boolean hasWarned = false;
		while(watchy.getElapsed(TimeUnit.SECONDS) < LOCK_TIME_FATAL_SECS) { 
			try {
				ok = globoLock.tryLock(LOCK_TIME_WARNING_SECS, TimeUnit.SECONDS);
			} catch (InterruptedException e) { ;}
			if(ok == true) {
				break;
			}
			if(watchy.getElapsed(TimeUnit.SECONDS) > LOCK_TIME_WARNING_SECS && hasWarned == false) {
				hasWarned = true;
				LOGGER.info("Long wait time on VaultManager tryLock, current locker is " + this.lockOwner);
			}
		}
		if(ok == false) {
			throw new CloudException("Lock timeout for site id " + siteId + ", lock owner is " + this.lockOwner);
		}
		this.lockOwner = lockOwner;
	}
	
	private void checkForIdleRegisters() {
		long maxMillis = VaultSettings.instance().getVaultLeaseAgeMaxMs();
		tryLock("checkForIdleRegisters");
		try {
			long shlong = System.currentTimeMillis();
			List<Integer> affected = new ArrayList<Integer>();
			for(Map.Entry<Integer,Long> e : leases.entrySet()) {
				if(shlong - e.getValue() > maxMillis
						|| shutdownNow == true) {
					affected.add(e.getKey());
				}
			}
			// Avoid modifying the very collection we're working on 
			for(int i : affected) {
				LOGGER.warn("Forcing cleanup for vault transaction " + i);
				// Need to undo this guy
				purge(i);
				unregister(i);
			}
			// Sanity-check
			if(leases.isEmpty()) {
				if(dogCrap.isEmpty() == false) {
					LOGGER.error("All vaults unregistered, but still " + dogCrap.size() + " actively locked prints");
				}
				//dogCrap.clearAll();
			}
		} finally {
			globoLock.unlock();
		} 
	}
	
	private void purge(int txNo) {
		Set<Print> awesome = dogCrap.getCacheForTx(txNo);
		Ods ds = DataStores.instance().getOds(0);
		ds.clearPrints(awesome);
	}
	
}

class CrapEntry {
	HashSet<Print> cache = new HashSet<Print>();
}

class DogCrap {
	private Map<Integer,CrapEntry> activeMap = new HashMap<Integer,CrapEntry>();
	private HashMap<Print,Integer> everyone = new HashMap<Print,Integer>();
	private static final Logger LOGGER = LoggerFactory.getLogger( VaultManager.class.getName() );
	
	boolean active(Print p) {
		return everyone.containsKey(p);
	}
	
	void put(int i, Print p) {
		CrapEntry ce = activeMap.get(i);
		if(ce == null) {
			ce = new CrapEntry();
			activeMap.put(i, ce);
		}
		int val = 0;
		try {
			val = everyone.get(p);
		} catch(NullPointerException npe) { ; }
		if(ce.cache.add(p)) {
			// Only add to "everyone" if we haven't before, otherwise it's 
			// a double-entry due to the nature of ADS munge..same block more than once
			everyone.put(p, ++val);
		}
		//LOGGER.debug("Val is currently " + val);
	}
	
	void remove(int i) {
		CrapEntry ce = activeMap.remove(i);
		if(ce == null) {
			return;
		}
		for(Print p : ce.cache) {
			int val = everyone.get(p);
			if(--val == 0) {
				everyone.remove(p);
			} else {
				everyone.put(p, val);
			}
		}
		// Sanity-check
		if(activeMap.isEmpty()) {
			if(everyone.isEmpty() == false) {
				LOGGER.warn("We have " + everyone.size() + " active prints being vaulted, but no active vaulters! Cleaning up.");
				everyone.clear();
			}
		}
	}
	
	boolean isEmpty() {
		return everyone.isEmpty();
	}
	
	int size() {
		return everyone.size();
	}
	
	/*void clearAll() {
		activeMap.clear();
		everyone.clear();
	}*/
	
	Set<Print> getCacheForTx(int tx) {
		CrapEntry crap = activeMap.get(tx);
		HashSet<Print> coolGuy = null;
		if(crap != null) {
			coolGuy = crap.cache;
		}
		if(coolGuy == null) {
			coolGuy = new HashSet<Print>();
		}
		
		return coolGuy;
	}
}
