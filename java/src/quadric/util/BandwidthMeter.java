package quadric.util;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO this class is not implemented yet
 *
 */
public class BandwidthMeter {
	private static final Logger LOGGER = LoggerFactory.getLogger( BandwidthMeter.class.getName() );
	
	Meter download;
	Meter upload;
	
	public static class Meter {
		private Meter parent = null;
		private double ratioMax = 0;
		private long maxBytesPerInterval = 0;
		private long maxBurstBytes = 0;
		private long intervalMillis;
		private int rollingAvIntervals;
		private volatile long bucket = 0;
		private long meteredBytes = 0;
		private volatile double meteredRate = 0;
		private Object lock = new Object();
		private Stopwatch rateTimer = new Stopwatch();
		private Stopwatch bucketTimer = new Stopwatch();
		
		private Meter(long maxPerInterval, int intervalMs, int rollingAvIntervals, long burst) {
			this.intervalMillis = intervalMs;
			this.maxBytesPerInterval = maxPerInterval;
			this.rollingAvIntervals = rollingAvIntervals;
			this.maxBurstBytes = burst;
			
		}
		
		/**
		 * Switch over to capping bandwidth to a ratio of the total available at the time
		 */
		public void useRatioCap(Meter parent, double ratio, long minBytesPerInterval) {
			this.parent = parent;
			this.ratioMax = ratio;
			this.maxBytesPerInterval = minBytesPerInterval;
			LOGGER.trace("Incoming ratio is " + ratioMax + " for instance " + this);
		}
		
		public double getRate() {
			// Update the rate if idle
			if(rateTimer.getElapsed(TimeUnit.MILLISECONDS) > (intervalMillis * rollingAvIntervals)) {
				synchronized(lock) {
					calcMeteredRate();
					fillBucket();
				}
			}
			return meteredRate;
		}
		
		public void meter(long used) { 
			// Sanity
			if(maxBurstBytes != 0 && used > maxBurstBytes) {
				// We need to do this to prevent deadlock
				maxBurstBytes = used;
			}
			// If maxBytesPerInterval is zero, they have no bandwidth cap
			if(maxBytesPerInterval != 0) {
				while(bucket < used && bucket != -1) {
					if(maxBytesPerInterval == 0) {
						// They changed their metering to zero...bastards
						break;
					}
					synchronized(lock) {
						try {
							lock.wait(200);
						} catch (InterruptedException e) {
							;
						}
						fillBucket();
					}
					
					
				}
				bucket -= used;
			} else if(rateTimer.getElapsed(TimeUnit.MILLISECONDS) > intervalMillis) {
				// No bandwidth cap is enforced, but we want to calculate metering
				calcMeteredRate();
			}
			meteredBytes += used;
		}
		
		public void setMaxPerInterval(long bytes) {
			maxBytesPerInterval = bytes;
			maxBurstBytes = bytes * 10;
		}
		
		private void fillBucket() {
			if(maxBytesPerInterval == 0) return;
			if(bucketTimer.getElapsed(TimeUnit.MILLISECONDS) < 10) return;
			long sinceLastBucketFilled = bucketTimer.getAndReset(TimeUnit.MILLISECONDS);
			double ratio = (double) sinceLastBucketFilled / (double) intervalMillis;
			long bytesToAdd = (long) (ratio * maxBytesPerInterval);
			bucket += bytesToAdd;
			if(bucket > maxBurstBytes && maxBurstBytes != 0) {
				bucket = maxBurstBytes;
			}
			lock.notifyAll();
		}
		
		private void calcMeteredRate() {
			if(rateTimer.getElapsed(TimeUnit.MILLISECONDS) > (intervalMillis * rollingAvIntervals)) {
				long elapsed = rateTimer.getAndReset(TimeUnit.MILLISECONDS);
				meteredRate = (double) meteredBytes / (double) elapsed * (double) intervalMillis;
				meteredBytes = 0;
				LOGGER.trace("Enforcing ratio is " + ratioMax + " for instance " + this);
				if(ratioMax != 0) {
					// Use a portion of parent bandwidth cap
					long maxBytesPerIntervalTemp = (long) (parent.maxBytesPerInterval * ratioMax); 
					
					// Enforce a sane minimum
					if(maxBytesPerIntervalTemp != 0 && maxBytesPerIntervalTemp < maxBytesPerInterval) {
						maxBytesPerIntervalTemp = maxBytesPerInterval;
					}
					maxBytesPerInterval = maxBytesPerIntervalTemp;
					// We want the burst to mirror what the parent permits
					maxBurstBytes = parent.maxBurstBytes;
					if(maxBurstBytes == 0 && maxBytesPerInterval != 0) {
						maxBurstBytes = maxBytesPerInterval /4;
					}
				}
			}
		}
		
		
		
	};
	
	public BandwidthMeter() {
		// Default to unlimited
		download = new Meter(0, 1000, 4, 0);
		upload = new Meter(0, 1000, 4, 0);
	}
	
	public BandwidthMeter(int kbPerSecondDown, int kbPerSecondUp) {
		setRates(kbPerSecondDown, kbPerSecondUp);
	}
	
	public void setRates(int kbPerSecondDown, int kbPerSecondUp) {
		long rateDown = (((long) kbPerSecondDown) * 1024L);
		long rateUp = (((long) kbPerSecondUp) * 1024L);
		long burstDown = rateDown * 1024L;
		long burstUp = rateUp * 1024L;
		download = new Meter(rateDown, 1000, 4, burstDown);
		upload = new Meter(rateUp, 1000, 4, burstUp);
	}
	
	public Meter getDownloadMeter() {
		return download;
	}
	
	public Meter getUploadMeter() {
		return upload;
	}
	
}
