package quadric.stats;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.BandwidthCloudAdapter;
import quadric.ods.JournalManager;
import quadric.util.Stopwatch;

public class StatsManager {
	private static final Logger LOGGER = LoggerFactory.getLogger( StatsManager.class.getName() );
	private static final int STATS_INTERVAL_MS = 1000;
	private static StatsManager me = new StatsManager();
	private Stopwatch w = new Stopwatch();
	private volatile boolean shouldRun = true; 
	private ConcurrentHashMap<String, Bucket> cMap = new ConcurrentHashMap<String,Bucket>();
	
	private StatsManager() { 
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdown();
		}));
		Thread t = new Thread(() -> {
			while(shouldRun) {
				if(w.getElapsed(TimeUnit.MILLISECONDS) > STATS_INTERVAL_MS) {
					w.reset();
					try {
						pulse();
					} catch(Exception e) {
						LOGGER.error("Error in stats", e);
					}
				}
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					;
				}
			}
			LOGGER.debug( "Shutdown complete");
		});
		t.start();
	}

	public static StatsManager instance() {
		return me;
	}
	
	public void register(String name, Supplier<Long> cb) {
		Bucket bucket = new Bucket();
		bucket.db = cb;
		cMap.put(name, bucket);
	}
	
	public void unregister(String name) {
		cMap.remove(name);
	}
	
	public void shutdown() {
		LOGGER.info("Cleanup commencing");
		shouldRun = false;
	}
	
	public void pulse() {
		if(new File("/tmp/metrics").exists() == false) {
			new File("/tmp/metrics").mkdir();
		}
		for(Map.Entry<String, Bucket> e : cMap.entrySet()) {
			long val = e.getValue().db.get();
			if(val > e.getValue().maxVal) {
				e.getValue().maxVal = val;
			}
			try (FileWriter rapper = new FileWriter("/tmp/metrics/" + e.getKey())) {
				rapper.append("" + val);
				rapper.append("\n" + e.getValue().maxVal);
			} catch(IOException ioe) {
				LOGGER.error("Unable to produce stats" + ioe);
			}
			
		}
	}
	
	
}

class Bucket {
	Supplier<Long> db;
	long maxVal = 0;
}
