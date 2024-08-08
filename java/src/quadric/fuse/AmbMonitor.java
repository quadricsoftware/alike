package quadric.fuse;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.BandwidthCloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.DataStores;
import quadric.blockvaulter.VaultSettings;
import quadric.ods.KurganAmbHeader;
import quadric.ods.Ods;
import quadric.restore.RestoreHeader;
import quadric.spdb.KurganBlock;
import quadric.spdb.KurganBlock.BlockSettings;
import quadric.util.DebugInputStream;
import quadric.util.DebugOutputStream;
import quadric.util.Print;
import quadric.util.SharedMem;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;

/**
 * Handles incoming AMB writes via Fuse
 *
 */
public class AmbMonitor {
	public static class AmbStats {
		public int blocksSent[] = new int[100];
		public int blocksSkipped[] = new int[100];
		public long bytesSent[] = new long[100];
		public long bytesSkipped[] = new long[100];
	}
	
	private static final Logger LOGGER = LoggerFactory.getLogger( AmbMonitor.class.getName() );

	private FuseMonitor daddy;
	private boolean paranoid;
	private BlockSettings bs = VaultSettings.instance().makeKurganSets();
	
	public AmbMonitor(FuseMonitor daddy) {
		this.daddy = daddy;
		
		paranoid = VaultSettings.instance().isParanoid();
		if(paranoid) {
			LOGGER.info("Paranoid mode enabled, all blocks will be checked during FUSE AMB munge");
		}
	}
	
	public void handleOpen(RestoreHeader header, String path) throws Exception {
		LOGGER.trace("New AMB open request for path " + path);
	}

	
	public void handleWrite(RestoreHeader header, String path) throws Exception {
		LOGGER.trace("Starting block stream for path " + path + " with header " + header);
		//int crc = daddy.shared.getCrc((int) header.pipeId);
		int txNo = getOrRegister(path);
		KurganBlockStream kbs = new KurganBlockStream(txNo);
		try {
			if(daddy.isPreshutdown() == true) {
				throw new IOException("FuseMonitor has been shut down");
			}
			doHandleWrite(header, path, kbs);
		} catch(Throwable e) {
			LOGGER.error("Error handling write for header " + header, e);
			if(kbs.error == null) kbs.error = e;
		} 
		//byte [] errorBytes = null;
		int errorNo = 0;
		if(kbs.error != null) {
			LOGGER.error("Returning error status to AMB client for " + path + ". Caused by: " + kbs.error);
			errorNo = -1;
		} else {
			//LOGGER.debug("No errors will be returned for AMB client " + header.pipeId + " (" + path + ")");
		}
		//daddy.shared.set((int) header.pipeId, errorNo, crc);

	}
	
	private void doHandleWrite(RestoreHeader header, String path, KurganBlockStream kbs) throws Exception {
		byte [] cruftstorm = new byte[header.length];
		/* Supplier<InputStream> supplier = () -> {
			try {
				return daddy.getOutPipe(header);
			} catch(IOException ioe) {
				throw new CloudException(ioe);
			}
		};
		try (InputStream is = new DebugInputStream(supplier, 5000)) {*/
		try (InputStream is = daddy.getOutPipe(header)) {
			// Read until we can't read anymore capt'n
			for(int x = 0; x < cruftstorm.length;) {
				int amt = is.read(cruftstorm, x, (cruftstorm.length - x));
				if(amt == -1) {
					throw new IOException("Unexpected EOF found! " + header);
				}
				x+= amt;
			}
		}
		LOGGER.trace("Done reading " + cruftstorm.length + " bytes off pipe for header " + header);
		
		kbs.write(cruftstorm);
		LOGGER.trace("Block is complete after " +  kbs.ambHeader.getBlockSize() + " bytes, will send to it to spdbs for header " + header);
		KurganBlock kb = kbs.getBlock();
		if(paranoid) {
			kb.calcMd5(VaultSettings.instance().makeKurganSets());
			if(kb.getMd5().equals(kbs.ambHeader.getPrint().toString()) == false) {
				throw new IOException("This block failed paranoid checks, " + kbs.ambHeader.getPrint());
				
			}
		}
		doWriteBlock(kb, path, kbs.txNo);
	}
	
	/**
	 * Send their payload to the data store
	 */
	private void doWriteBlock(KurganBlock kb, String path, int txNo) {
		AmbHelper.doWriteBlock(bs, kb, path, txNo);
		
	}
	
	
	private int getOrRegister(String path) {
		return AmbHelper.getOrRegister(path);
	}
	
	
}

class KurganBlockStream {
	private static final Logger LOGGER = LoggerFactory.getLogger( KurganBlockStream.class.getName() );
	private static final int BLOCK_SIZE = 1024 * 512;
	
	byte [] blockBites = new byte[KurganBlock.calcMaxBlockSize(BLOCK_SIZE)];
	byte [] ambHeaderBites = new byte[40];
	int txNo = -1;
	KurganAmbHeader ambHeader = new KurganAmbHeader();
	Throwable error = null;
	
	KurganBlockStream(int txNo) {
		this.txNo = txNo;
	}
	
	
	void write(byte [] bites) throws IOException {
		LOGGER.trace(this.toString() + " writing bites of length " + bites.length);
		//int internalOffset = 0;
		// Handle the AMB header area
		int amt = 40;
		
		if(bites.length < amt) {
			throw new IOException("Write too short: " + bites.length);
		}
		System.arraycopy(bites, 0, ambHeaderBites, 0, amt);
		ambHeader.load(ambHeaderBites);
		//pos += amt;
		
		// Handle the kurgan block
		int tots = ambHeader.getBlockSize() + 40;
		if(tots > bites.length) {
			throw new IOException("Block underwrite (" + tots + " expected vs " + bites.length + " received)");
		}
		LOGGER.trace("About to array copy in the amount of " + ambHeader.getBlockSize());
		System.arraycopy(bites, 40, blockBites, 0, ambHeader.getBlockSize());
		
	}
	
	KurganBlock getBlock() {
		return new KurganBlock(blockBites, ambHeader.getBlockSize(), ambHeader.getPrint().toString());	
	}
}
