package quadric.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;

public class HclReaderUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger( HclReaderUtil.class.getName() );
	public static final int RECORD_SIZE = Print.PRINT_SIZE + 1;
	public static final int RECORD_SIZE_LONG = 33;
	public static final int PRINT_PREFETCH_COUNT = 1024;
	public static final String CODA = "CODA";
	private String path;
	private LinkedList<Print> list = new LinkedList<Print>();
	private int size = 0;
	private int pos = 0;
	private int recordSize = 0;
	private int offset = 0;
	private int prefetch;
	private int codaLength;
	
	public HclReaderUtil(String path) {
		this(path, 0);
	}
	public HclReaderUtil(String path, int offset) {
		this(path, offset, PRINT_PREFETCH_COUNT);
	}
	
	public HclReaderUtil(String path, int offset, int cacheSize) {
		prefetch = cacheSize; 
		this.offset = offset;
		this.path = path;
		File file = new File(path);
		if(file.exists() == false) {
			throw new CloudException("No HCL found at path " + path);	
		} 
		if(file.length() == 0) {
			throw new CloudException("HCL is size zero at path " + path);
		}
		codaLength = CODA.length();
		try (FileChannel ch = FileChannel.open(Paths.get(path), StandardOpenOption.READ)) {
			if(offset != 0) {
				// Their HCL starts deeper into the file
				ch.position(offset);
			}
			// Determine record size
			byte [] garbage = VaultUtil.ezLoad(ch, RECORD_SIZE);
			if(garbage == null || garbage.length < 17) {
				// It's emtpy, or at least assume so
				recordSize = RECORD_SIZE;
			} else {
			
				if(garbage[16] == '\n') {
					recordSize = RECORD_SIZE;
				} else {
					recordSize = RECORD_SIZE_LONG;
				}
			}
			// Verify coda
			int newPos = (int) file.length() - codaLength;
			// Adjust for initial read
			ch.position(newPos);
			byte [] tossMe = VaultUtil.ezLoad(ch, CODA.length());
			if(tossMe == null || new String(tossMe, "US-ASCII").equals(CODA) == false) {
				// Try again, accounting for MD5 sig
				ch.position(newPos - 16);
				tossMe = VaultUtil.ezLoad(ch, CODA.length());
				if(new String(tossMe, "US-ASCII").equals(CODA) == false) {
					throw new CloudException("Missing coda at " + path);
				}
			    // Factor in MD5 footer
				codaLength += 16;
			}
		} catch(IOException ex) {
			throw new CloudException(ex);
		}
		size = (int) (file.length() - codaLength - offset) / recordSize;
		
	}
	
	public HclCursor createCursor() {
		return createCursor(0, 0);
	}
	
	public boolean alreadyHasMd5() {
		return (codaLength != CODA.length());
	}
	
	/**
	 * 
	 * @param initialPrint the first print to curse from
	 * @param maxPrint the maximum print to curse from
	 * @return a cursor representing the prints
	 */
	public HclCursor createCursor(int initialPrint, int maxPrint) {
		// To they want to end early?
		if(maxPrint != 0) {
			size = maxPrint + initialPrint;
		}
		return new HclCursor(){
			int myPos = initialPrint;
			public boolean hasNext() {
				if(myPos < size) {
					return true;
				}
				return false;
			}
			public Print next() {
				return getAt(myPos++);
			}
			public int getPosition() {
				return pos;
			}
			@Override
			public int count() {
				return size - initialPrint;
			}
			
			public byte [] bulk(int count) {
				byte [] cool = bulky(myPos, count, size);
				if(cool != null) {
					myPos += (cool.length / recordSize);
				}
				return cool;
			}
		};
	}
	
	private Print getAt(int position) { 
		if(position >= size) {
			throw new CloudException("Out of bounds");
		}
		if(position != pos) {
			list.clear();
		}
		if(list.isEmpty()) {
			loadCache(position);
			pos = position;
		}
		pos++;
		return list.pop();
		
	}
	
	private byte [] bulky(int position, int quantity, int maxPrint) {
		try (FileInputStream fis = new FileInputStream(path)) {
			// Skip to beginning area
			long skipTo = offset + (position * recordSize);
			//LOGGER.debug("Skipping to ECL location " + skipTo);
			if(fis.skip(skipTo) != skipTo) {
				throw new CloudException("Skip failed on " + path);
			}
			boolean needsCoda = false;
			//LOGGER.debug("Position of " + position + ", quantity of " + quantity + ", maxPrint of " + maxPrint);
			if(position + quantity >= maxPrint) {			
				quantity = maxPrint - position;
				//LOGGER.debug("New quantity will be " + quantity);
			}
			if(quantity < 0) {
				// End of line
				return null;
			}
			int len = quantity * recordSize;
			if(needsCoda) {
				len +=4;
			}
			byte [] awesome = new byte[len];
			VaultUtil.ezLoad(fis, awesome, awesome.length);
			return awesome;
		} catch(Exception e) {
			throw new CloudException(e);
		}
	}
	
	private void loadCache(int pos) {
		if(list.isEmpty() == false) {
			throw new CloudException("Expected lifo to be empty");
		}
		long t1 = System.nanoTime();
		try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(path))) {
			int count = 0;
			// Skip to beginning area
			fis.skip(offset + (pos * recordSize));
			byte [] chunky = new byte[32];
			while(count++ < prefetch) {
				Print cord = null;
				if(recordSize == RECORD_SIZE) {
					cord = new Print();
					if(fis.read(cord.bytes) != recordSize -1) {
						break;
					} 
				} else {
					if(fis.read(chunky) != recordSize -1) {
						break;
					}
					cord = new Print(new String(chunky, "US-ASCII"));
				}
				// For the newline
				fis.skip(1);
				list.add(cord);
			}
		} catch(Exception e) {
			throw new CloudException(e);
		}
		long t2 = System.nanoTime();
		double awesome = ((double) t2 - t1) / (1000000.00D);
		LOGGER.trace("Read " + list.size() + " prints off disk in " + String.format("%.2f", awesome) + "ms");
	}
	
	
}
