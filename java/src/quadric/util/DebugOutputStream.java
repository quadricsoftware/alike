package quadric.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import quadric.blockvaulter.CloudException;

/**
 * Useful for debugging streams that block, like pipes and sockets
 *
 */
public class DebugOutputStream extends OutputStream {
	OutputStream os;
	long timeout;
	static ExecutorService exec = Executors.newCachedThreadPool();
	
	public DebugOutputStream(Supplier<OutputStream> supp, long timeout) {
		this.timeout = timeout;
		Future<Integer> future = exec.submit( () -> {
			this.os = supp.get();
			return 0;
		});
		try {
			future.get(timeout, TimeUnit.MILLISECONDS);
		} catch(Exception e) {
			throw new CloudException(e);
		}
	}
	
	
	
	@Override
	public void write(int b) throws IOException {
		Future<Integer> future = exec.submit( () -> {
			os.write(b);
			return 0;
		});
		try {
			future.get(timeout, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
	
	@Override 
	public void write(byte [] b) throws IOException {
		write(b, 0, b.length);
	}
	
	@Override
	public void write(byte [] b, int off, int len) throws IOException {
		Future<Integer> future = exec.submit( () -> {
			os.write(b, off, len);
			return 0;
		});
		try {
			future.get(timeout, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			throw new IOException(e);
		}
		
	}
	
	@Override
	public void close() throws IOException {
		os.close();
	}
	

}
