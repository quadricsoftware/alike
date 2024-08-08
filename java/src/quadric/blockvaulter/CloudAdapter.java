package quadric.blockvaulter;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.util.Print;
import quadric.util.Stopwatch;

public abstract class CloudAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( CloudAdapter.class.getName() );
	
	private static final int READ_ONLY_RECHECK_PERIOD_MINS = 5;
	private static final String READ_ONLY_BASE_FILE = "/mnt/ads/read_only";
	
	private Stopwatch watchy = null;
	private boolean cachedReadOnlyState = false;
	protected int siteId = -1;
	
	/**
	 * 
	 * @param path the name of the object to store. Blocks must be hex without anything else in the path
	 * @param in the stream representing the stuff
	 * @param len the length of the block
	 * @param dr5 the md5 of the block. Required for blocks but not for metadata
	 * @return true if something was written. Some implementations may return false for block data if the block is already housed.
	 */
	public abstract boolean putBlock(String path, InputStream in, long len, String dr5);
	public abstract GetResult getBlock(String path, long max);
	public abstract boolean stat(String path);
	public abstract void del(String path);
	public abstract String id(String path);
	public abstract DataStoreType getType();
	
	private CloudAdapter() { ;}
	protected CloudAdapter(int siteId) { 
		this.siteId = siteId;
	}
	
	
	public synchronized boolean isReadOnly() {
		if(watchy != null && watchy.getElapsed(TimeUnit.MINUTES) < READ_ONLY_RECHECK_PERIOD_MINS) {
			return cachedReadOnlyState;
		}
		boolean oldCachedState = cachedReadOnlyState;
		cachedReadOnlyState = false;
		if(new File(READ_ONLY_BASE_FILE).exists()) {
			cachedReadOnlyState = true;
		} else {
			// Also check for a missing "journals", which means the data store was unmounted
			String fileStr = "/mnt/ads/journals";
			if(siteId == 1) {
				fileStr = "/mnt/ods1/journals";
			}
			if(new File(fileStr).exists() == false) {
				cachedReadOnlyState = true;
			}
		}
			
		// Ensure one-time boot-up check of read-only state
		if(watchy == null) {
			watchy = new Stopwatch();
		} else if(oldCachedState != cachedReadOnlyState){
			LOGGER.error("****READ-ONLY STATE TOGGLED TO " + cachedReadOnlyState + " for site " + siteId);
		}
		watchy.reset();
		return cachedReadOnlyState;
	}
	
	
	
	public boolean cantVerifyKurganBlocks() {
		return false;
	}

	
	
	/**
	 * Adapters that support a filesystem can return something useful here. Otherwise defaults to returning null.
	 * @param p 
	 * @return
	 */
	public String getBlockPath(Print p) {
		return null;
	}

	
}


