package quadric.util;

import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Native;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.VaultSettings;
import quadric.spdb.SimpleAdapter;

/**
 * Flocks a file at the path passed in
 *
 */
public class Flocker implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger( Flocker.class.getName() );
	private static final int FLOCK_TIMEOUT_SECS = 90;
	private static final int FLOCK_WARNING_SECS = 10;
	private int nativeLock;
	private String path;
	
	static {
		String nativePath = VaultSettings.instance().getNativeLibraryPath();
		nativePath = nativePath + "/qjni.so";
		try {
			System.load(nativePath);
			
		} catch(Exception e) {
			LOGGER.error("Unable to load flock library at " + nativePath);
		}
		         
	}
	
	public Flocker() {
	}

	public Flocker(String path) throws IOException {
		open(path);
	}
	
	public void open(String path) throws IOException {
		this.path = path;
		//LOGGER.debug("Entering open of Flocker for " + path);
		Stopwatch watchy = new Stopwatch();
		boolean hasLoggedWarn = false;
		while(watchy.getElapsed(TimeUnit.SECONDS) < FLOCK_TIMEOUT_SECS) {
						
			nativeLock = 0;
			//LOGGER.debug("Calling getNativeLock on " + path);
			nativeLock = getNativeLock(path.getBytes("US-ASCII"));
			//LOGGER.debug("GetNativeLock returned " + nativeLock + " for " + path);
			if(nativeLock != 0) {
				//LOGGER.debug("break condition met for " + path);
				break;
			}
			
			if(watchy.getElapsed(TimeUnit.SECONDS) > FLOCK_WARNING_SECS && hasLoggedWarn == false) {
				hasLoggedWarn = true;
				LOGGER.warn("Waiting on flock at path " + path + " for " + FLOCK_WARNING_SECS + " seconds, max wait is " + FLOCK_TIMEOUT_SECS);
			}
			try { 
				Thread.sleep(200); 
			} catch(Throwable t) { ;}
		}
		if(nativeLock == 0) {
			throw new IOException("Failed to acquire flock on " + path + " after " + watchy.getElapsed(TimeUnit.SECONDS) + " seconds");
		}
		//LOGGER.debug("Ended flocker kool and the gang for " + path);
	}

	@Override
	public void close() throws IOException {
		if(nativeLock != 0) {
			//LOGGER.debug("Tearing down flock on FD " + nativeLock);
			closeNativeLock(nativeLock);
		} else {
			if(path == null) return;
			LOGGER.error("Attempt to unlock a flock that was never locked at path " + path);
		}
		
	}
	
	public native int getNativeLock(byte[] path);
	
	public native void closeNativeLock(int l);

}
