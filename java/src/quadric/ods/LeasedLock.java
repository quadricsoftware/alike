package quadric.ods;

import java.io.Closeable;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.util.VaultUtil;

/**
 * A long-term lock on particular business function. Not suitable for fine-grained contention management.
 *
 */
public class LeasedLock implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger( LeasedLock.class.getName() );
	private long lastLeaseTime = 0;
	private int interval = 0;
	private CloudAdapter adp;
	private String path;
	private volatile boolean isLeasing = false;
	private Timer timer = new Timer();
	
	public static boolean isLocked(CloudAdapter adp, String path, int intervalSecs) {
		try {
			if(adp.stat(path) == false) {
				return false;
			}
			String wad = VaultUtil.getBlockToString(adp, path);
			long time = Long.parseLong(wad);
			if(System.currentTimeMillis() - time > (intervalSecs * 1000)) {
				return false;
			}
			return true;
		} catch(Exception e) {
			throw new CloudException(e);
		}
		
		
	}

	public LeasedLock(CloudAdapter adp, String path, int expTimeSecs) {
		this.adp = adp;
		this.path = path;
		this.interval = expTimeSecs;
		lease();
	}
	
	@Override
	public void close() throws IOException {
		unlock();
	}
	
	public synchronized boolean isLeaseOk() {
		if(System.currentTimeMillis() - lastLeaseTime > (interval * 1000)) {
			return false;
		} 
		return true;
	}
	
	public synchronized void unlock() {
		LOGGER.debug( "Terminating leasing...");
		isLeasing = false;
		timer.cancel();
		
		// Kill it with fire
		if(adp.stat(path)) {
			adp.del(path);
		}
		lastLeaseTime = 0;
		
	}
	
	public void lock() {
		if(!isLeasing) {
			LOGGER.debug( "Leasing will start shortly for path " + path);
			isLeasing = true;
			lease();
		}
	}
	
	private void lease() {
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				Thread.currentThread().setName("LeasedLock");
				doLease();
			}
		}, 0, interval * 1000);
	}
	
	private synchronized void doLease() {
		LOGGER.trace("Leased lock doing lease task");
		if(isLeasing == false) {
			LOGGER.debug( "Leased lock skipping last lease");
			// Don't renew the least if someone else ended it
			timer.cancel();
			return;
		}
		long now = System.currentTimeMillis();
		String wad = "" + now;
		try {
			byte [] bites = wad.getBytes("US-ASCII");
			VaultUtil.putBlockFromMem(bites, bites.length, adp, path);
			lastLeaseTime = now;
		} catch(Exception e) {
			throw new CloudException(e);
		}
	}
	

}
