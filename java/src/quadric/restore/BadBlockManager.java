package quadric.restore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.DoubleConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.ods.EclReader;
import quadric.ods.VmVersion;
import quadric.socket.MungeServer;
import quadric.util.HclCursor;
import quadric.util.HclReaderUtil;
import quadric.util.HclWriterUtil;
import quadric.util.JobControl;
import quadric.util.PathedGetResult;
import quadric.util.Print;
import quadric.util.VaultUtil;

public class BadBlockManager {
	private static final Logger LOGGER = LoggerFactory.getLogger( BadBlockManager.class.getName() );
	private static final String BAD_BLOCKS_OBJECT = "000.bcl";
	private static final int MAX_BAD_PRINTS = 1000 * 50;
	private static final long SLEEP_INTERVAL_SECS = 70;
	
	private TreeSet<Print> badPrints = new TreeSet<Print>(); 
	private int siteId;
	private volatile boolean isDirty;
	private volatile boolean shouldRun = true;
	private Thread t;
	
	public BadBlockManager(int siteId) {
		this.siteId = siteId;
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		if(adp.stat(BAD_BLOCKS_OBJECT) == true) {
		
			PathedGetResult rez = VaultUtil.getBlockViaFile(adp, BAD_BLOCKS_OBJECT);
			try (InputStream is = (rez.in)) {
				HclReaderUtil hcl = new HclReaderUtil(rez.localPath);
				HclCursor c = hcl.createCursor();
				for(int x = 0; x < MAX_BAD_PRINTS; ++x) {
					if(c.hasNext() == false) {
						break;
					}
					badPrints.add(c.next());
				}	
			} catch(IOException ioe) { 
				LOGGER.error("Unable to load damage block information for site " + siteId);
				try {
					// Cleanup
					adp.del(BAD_BLOCKS_OBJECT);
				} catch(CloudException ce) { ;}
			}
		}
		if(badPrints.size() > 0) {
			LOGGER.error("Site " + siteId + " has " + badPrints.size() + " known bad blocks. Will attempt to repair them during backup/vaults.");
		}
		t = new Thread(() -> {
			while(shouldRun) {
				if(isDirty) {
					serialize();
				}
				try {
					Thread.sleep(SLEEP_INTERVAL_SECS * 1000);
				} catch (InterruptedException e) {
					;
				}
			}
		});
		t.start();
	}
	
	public void shutdown() {
		shouldRun = false;
		t.interrupt();
		serialize();
	}
	
	public synchronized boolean isDamaged(Print p) {
		return badPrints.contains(p);
	}
	
	public synchronized void mark(Print p) { 
		if(badPrints.size() > MAX_BAD_PRINTS) {
			return;
		}
		badPrints.add(p);
		isDirty = true;
	}
	
	public synchronized void mark(List<Print> badBlocks) {
		if(badBlocks.isEmpty()) return;
		if(badPrints.size() > MAX_BAD_PRINTS) {
			return;
		}
		badPrints.addAll(badBlocks);
		while(badPrints.size() > MAX_BAD_PRINTS) {
			badPrints.remove(badPrints.first());
		}
		isDirty = true;
	}
	
	public synchronized void heal(Print p) {
		badPrints.remove(p);
		isDirty = true;
		LOGGER.debug("Healed block " + p + " for site id " + siteId);
	}
	
	public synchronized void heal(List<Print> healed) {
		if(healed.isEmpty()) return;
		if(badPrints.isEmpty()) return;
		int beforeSize = badPrints.size();
		badPrints.removeAll(healed);
		// Did anything happen?
		if(beforeSize == badPrints.size()) {
			return;
		}
		isDirty = true;
		LOGGER.debug("Healed " + healed.size() + " blocks for site id " + siteId);
		
	}
	
	public List<Print> allBad() {
		return new ArrayList<Print>(badPrints);
	}
	
	private synchronized void serialize() {
		if(isDirty == false) {
			return;
		}
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		if(adp.isReadOnly()) {
			return;
		}
		String hclTemp = null;
		try {
			LOGGER.debug("BadBlockManager serializing " + badPrints.size() + " problematic blocks for site " + siteId + " to journal "  + BAD_BLOCKS_OBJECT);
			long aproxSize = badPrints.size() * HclReaderUtil.RECORD_SIZE;
			File scrapper = File.createTempFile("lookup", "rlog", new File(VaultSettings.instance().getTempPath(aproxSize)));
			scrapper.deleteOnExit();
			hclTemp = scrapper.toString();
			HclWriterUtil writer = new HclWriterUtil(hclTemp);
			badPrints.forEach( p -> {
				writer.writePrint(p);
			});
			writer.writeCoda();
			VaultUtil.putBlockFromFile(adp, hclTemp, BAD_BLOCKS_OBJECT, null, null);
			isDirty = false;
		} catch(Throwable t) {
			LOGGER.error("Error saving bad block state for site " + siteId, t);
		} finally {
			if(hclTemp != null) {
				new File(hclTemp).delete();
			}
		}
		
	}


	
}
