package quadric.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.ods.Ods;

/**
 * Used to control a job
 *
 */
public class JobControl {
	private static final Logger LOGGER = LoggerFactory.getLogger( JobControl.class.getName() );
	private volatile boolean canceled = false;
	private volatile boolean paused = false;
	
	
	private static Map<JobControl,JobControl> shutdownHookers = Collections.synchronizedMap(new WeakHashMap<JobControl,JobControl>());
	private static ScheduledExecutorService stupid = Executors.newSingleThreadScheduledExecutor();
	
	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdown();
		}));
	}
	
	public static synchronized void shutdown() {
		if(shutdownHookers.isEmpty() == false) {
			LOGGER.info("Shutdown event trapped; canceling " + shutdownHookers.size() + " references");
		}
		shutdownHookers.forEach((k,v) -> v.cancel());
	}
	
	public JobControl() {
		// We need to delay adding this to the the hookers because "this" wont be a valid reference until the future
		stupid.schedule( () -> {
				Thread.currentThread().setName("ShutdownHookDelay");
				try {shutdownHookers.put(this,this); } catch(Throwable t) { ; }
		}, 10000, TimeUnit.MILLISECONDS);
		
	}
	
	public void cancel() {
		canceled = true;
	}
	
	public boolean isCanceled() {
		return canceled;
	}
	
	public  boolean togglePause(boolean shouldPause)  {
		if(paused == shouldPause) {
			return false;
		}
		paused = shouldPause;
		if(paused == false) {
			synchronized(this) {
				this.notifyAll();
			}
		}
		return true;
	}
	
	public synchronized void control() {
		if(canceled) {
			throw new JobCancelException();
		}
		while(paused && ! canceled) {
			try {
				synchronized(this) {
					this.wait(1000);
				}
			} catch (InterruptedException e) {
				;
			}
		}
	}
	
	
	
}
