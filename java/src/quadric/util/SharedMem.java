package quadric.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.zip.CRC32;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;

public class SharedMem implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger( SharedMem.class.getName() );
	
	static {
		String nativePath = VaultSettings.instance().getNativeLibraryPath();
		nativePath = nativePath + "/qmem.so";
		try {
			System.load(nativePath);
			
		} catch(Exception e) {
			LOGGER.error("Unable to load sharedmem library at " + nativePath);
		}
		         
	}
	
	public SharedMem(int clientNo) {
		open(clientNo);
	}
	
	
	public void open(int clientNo) {
		LOGGER.debug("Loading shared memory server for client " + clientNo);
		int retVal = ninit(clientNo);
		if(retVal < 0) {
			throw new CloudException("Unable to open shared memory");
		}
		LOGGER.debug("Shared memory has been loaded.");
	}
	
	/**
	 * 
	 * @param slot
	 * @param val
	 */
	public void writeAll(int slot, byte [] buffer) {
		//LOGGER.debug("About to write " + buffer.length + " to sharedmem area");
		//CRC32 crc = new CRC32();
		//crc.update(struct.store());
		//int foo = (int) crc.getValue();
		/* if(buffer.length > (1024 * 1024 * 9)) {
			throw new IllegalArgumentException("Attempt to overflow memory buffer");
		}*/
		int retVal = nset(slot, buffer);
		if(retVal < 0) {
			throw new CloudException("Unable to write to shared memory, likely timeout");
		}
		//LOGGER.debug("Write of " + buffer.length + " complete with return code " + retVal);
	}
	
	@Override
	public void close() throws IOException {
		nclose();
		
	}
	
	private native int ninit(int clientNo);
	private native void nclose();
	private native int nset(int slotNo, byte [] buffer);

}
