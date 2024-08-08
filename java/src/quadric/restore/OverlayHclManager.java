package quadric.restore;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.util.Print;
import quadric.util.VaultUtil;

/**
 * Represents an "incremental" backup written directly to the FUSE layer
 * In particular, this is the HCL state during the write process
 * 
 * We use a queue to make writes efficient, so we're not too chatty with
 * the overlay file
 * 
 *
 */
public class OverlayHclManager {
	
	private static OverlayHclManager me = new OverlayHclManager();
	
	private OverlayHclManager() { ; }
	
	Map<Integer,OverlayMeta> files = new HashMap<Integer,OverlayMeta>();
	
	public static OverlayHclManager instance() {
		return me;
	}
	
	public void register(int txNo, String sourceHcl) {
		synchronized(this) {
			if(files.containsKey(txNo)) {
				throw new CloudException("Transaction " + txNo + " already registered");
			}
		}
		long sz = new File(sourceHcl).length();
		String overlayPath = VaultSettings.instance().getTempPath(sz);
		overlayPath += "/" + txNo + "_" + System.currentTimeMillis() + ".hcl";
		String rez;
		try {
			rez = VaultUtil.ezExec("cp -f $1 $2", sourceHcl, overlayPath);
		} catch (IOException e) {
			throw new CloudException(e);
		}
		if(rez.trim().isEmpty() == false) {
			throw new CloudException("Creation (cp) of source HCL " + sourceHcl + " to destination " + overlayPath + " failed with error " + rez);
		}
		OverlayMeta coolGuy = new OverlayMeta(overlayPath);
		synchronized(this) {
			files.put(txNo, coolGuy);
		}
		
	}
	
	public void overlay(int txNo, String md5, int blockNum) {
		OverlayMeta coolGuy = obtain(txNo);
		
		coolGuy.queue.add(new OverlayItem(blockNum, new Print(md5)));
		coolGuy.flushIfNeeded();
		
	}
	
	public String release(int txNo) {
		OverlayMeta coolGuy = obtain(txNo);
		coolGuy.flush();
		synchronized(this) {
			files.remove(txNo);
		}
		return coolGuy.overlayHcl;
	}
	
	private synchronized OverlayMeta obtain(int txNo) {
		OverlayMeta coolGuy = files.get(txNo);
		if(coolGuy == null) {
			throw new CloudException("Transaction " + txNo + " not found!");
		}
		return coolGuy;
	}
}

class OverlayMeta {
	static final int FLUSH_Q_SIZE = 500; 
	String overlayHcl;
	Deque<OverlayItem> queue = new LinkedList<OverlayItem>();
	
	OverlayMeta(String overlayHcl) {
		this.overlayHcl = overlayHcl;
	}
	
	void flushIfNeeded() {
		if(queue.size() > FLUSH_Q_SIZE) {
			flush();
		}
	}
	
	void flush() {
		try (RandomAccessFile rando = new RandomAccessFile(overlayHcl, "rw")) {
			for(OverlayItem tm : queue) {
				long offset = tm.blockNum * Print.PRINT_SIZE +1;
				rando.seek(offset);
				rando.write(tm.md5.bytes);
			}
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		queue.clear();
	}
}

class OverlayItem {
	int blockNum;
	Print md5;
	OverlayItem(int blockNum, Print p) {
		this.blockNum = blockNum;
		this.md5 = p;
	}
}