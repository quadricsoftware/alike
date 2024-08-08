package quadric.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import quadric.blockvaulter.CloudException;


/**
 * Useful for debugging streams that block, like pipes and sockets
 *
 */
public class DebugInputStream extends InputStream {
	InputStream is;
	long timeout;
	static ExecutorService exec = Executors.newCachedThreadPool(); 
	
	public DebugInputStream(Supplier<InputStream> supp, long timeout) {
		this.timeout = timeout;
		Future<Integer> future = exec.submit( () -> {
			this.is = supp.get();
			return 0;
		});
		try {
			future.get(timeout, TimeUnit.MILLISECONDS);
		} catch(Exception e) {
			throw new CloudException(e);
		}
	}
	
	
	@Override
	public int read() throws IOException {
		Future<Integer> future = exec.submit( () -> {
			return is.read();
		});
		try {
			return future.get(timeout, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			throw new IOException(e);
		}	
	}
	
	@Override 
	public int read(byte [] bites)  throws IOException {
		return read(bites, 0, bites.length);
	}
	
	@Override 
	public int read (byte [] b, int off, int len)  throws IOException {
		Future<Integer> future = exec.submit( () -> {
			return is.read(b, off, len);
		});
		try {
			return future.get(timeout, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
	
	@Override
	public void close() throws IOException {
		is.close();
	}

}
