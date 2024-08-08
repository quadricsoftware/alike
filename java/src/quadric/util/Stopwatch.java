package quadric.util;

import java.util.concurrent.TimeUnit;

public class Stopwatch {
	private volatile long last;
	private long paused;
	
	public Stopwatch() {
		last = System.nanoTime();
	}
	
	public long getElapsed(TimeUnit unit) {
		long diff = System.nanoTime() - last;
		return unit.convert(diff, TimeUnit.NANOSECONDS);
	}
	
	public long getAndReset(TimeUnit unit) {
		long now = System.nanoTime();
		long diff = now - last;
		last = now;
		return unit.convert(diff, TimeUnit.NANOSECONDS);
	}

	public void reset() {
		last = System.nanoTime();
	}
	
	public void pause() {
		paused = System.nanoTime();
	}
	
	public void unPause() {
		long diff = System.nanoTime() - paused;
		last += diff;
	}
}
