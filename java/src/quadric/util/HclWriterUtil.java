package quadric.util;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;

/**
 * Currently only supports short prints
 *
 */
public class HclWriterUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger( HclWriterUtil.class.getName() );
	private String path;
	private int bufferMax; 
	private int recordSize;
	private ByteBuffer buffy;
	private long skipAmt =0;
	private long offsetAmt = 0;
	
	public HclWriterUtil(String path) {
		this(path, 0);
	}
	
	public HclWriterUtil(String path, long skipAmt, boolean useLongPrints) {
		init(path, skipAmt, useLongPrints);
	}
	
	public HclWriterUtil(String path, long skipAmt) {
		init(path, skipAmt, false);
	}
	
	private void init(String path, long skipAmt, boolean useLongPrints) {
		int mult = 10;
		recordSize = HclReaderUtil.RECORD_SIZE;
		if(useLongPrints) {
			recordSize = HclReaderUtil.RECORD_SIZE_LONG;
			mult = 5;
		}
		bufferMax = 1024 * mult * recordSize;
		buffy = ByteBuffer.allocate(bufferMax);
		
		this.skipAmt = skipAmt;
		this.offsetAmt = skipAmt;
		this.path = path;
		File f = new File(path);
		if(f.exists() == true && f.length() > 0)
			if(skipAmt == 0) {
			LOGGER.debug("HCL already exists and will overwritten at path " + path);
			truncate(0);
		} else {
			LOGGER.trace("Adding to existing HCL at path " + path + " and offset " + skipAmt);
			//truncate(skipAmt);
		}
	}
	
	public void writePrint(Print p) {
		try {
			// Short or long prints?
			if(recordSize == HclReaderUtil.RECORD_SIZE) {
				buffy.put(p.bytes);
			} else {
				buffy.put(p.toString().toLowerCase().getBytes("US-ASCII"));
			}
			buffy.put((byte) '\n');
			if(buffy.remaining() <= recordSize) {
				flush();
			}
		} catch(Exception e) {
			throw new CloudException(e);
		}
	}
	
	public void writeCoda() {
		buffy.put((byte) 'C');
		buffy.put((byte) 'O');
		buffy.put((byte) 'D');
		buffy.put((byte) 'A');
		flush();
		File f = new File(path);
		long baseLen = f.length() - HclReaderUtil.CODA.length();
		baseLen -= skipAmt;
		if(baseLen % recordSize != 0) {
			throw new CloudException("HCL format corrupted during write process");
		}
	}
	
	public void flush() {
		long t1 = System.nanoTime();
		int sz = buffy.position();
		buffy.flip();
		try (FileChannel fc = FileChannel.open(Paths.get(path),  StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
			fc.position(offsetAmt);
			VaultUtil.ezStore(fc, buffy);
			offsetAmt += sz;
			buffy.clear();
		} catch(Exception e) {
			throw new CloudException(e);
		}	
		long t2 = System.nanoTime();
		double awesome = ((double) t2 - t1) / (1000000.00D);
		LOGGER.trace("Fushed " + sz + " bytes from HCL writer in " + String.format("%.2f", awesome) + "ms");
	}
	
	private void truncate(long sz) {
		try (FileOutputStream fos = new FileOutputStream(path, true)){
			FileChannel outChan = fos.getChannel();
			outChan.truncate(sz);
		} catch(Exception e) {
			throw new CloudException(e);
		}
	}

	public String getPath() {
		return path;
	}

}
