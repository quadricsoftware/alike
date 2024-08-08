package quadric.blockvaulter;

import java.util.Collections.*;
import java.util.*;
import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.ods.EclDisk;
import quadric.ods.EclReader;
import quadric.restore.EclCacheKey;
import quadric.restore.RestoreMonitor;
import quadric.spdb.KurganBlock.BlockSettings;
import quadric.util.HclCursor;
import quadric.util.Print;
import quadric.util.VaultUtil;


public class RlogCache {
	private static final Logger LOGGER = LoggerFactory.getLogger( RlogCache.class.getName() );
	String eclPath;
	Map<Integer,Print> cache;
	int prefetch;
	int diskNum;
	long diskSize;
	int blockSize;
	Object misc;
	EclCacheKey myKey;
		
	
	public RlogCache(CloudAdapter adp, String ecl, int diskNum, int cacheCount, int prefetch, Object misc, int siteId, long flagId) {
		this.misc = misc;
		//int blockSize = Integer.parseInt(VaultSettings.instance().getSettings().get("blockSize")) * 1024;
		this.eclPath = ecl;
		this.prefetch = prefetch;
		this.diskNum = diskNum;
		this.myKey = new EclCacheKey(siteId, flagId, diskNum);
		cache = new LinkedHashMap(cacheCount +1, .75F, true) {
			public boolean removeEldestEntry(Map.Entry eldest) {
				return size() > cacheCount;
			}
		};
		// Calculate "true" disk size
		long entryCount = 0;
		String eclPath = ecl; 
		
		EclReader eclReader = new EclReader(eclPath);
		EclDisk duck = eclReader.getDisks().get(diskNum);
		entryCount = duck.getHclCount();
		
		// Determine blocksize
		BlockSettings sex = VaultSettings.instance().makeKurganSets();
		Print p = getEntry(0, adp);
		byte[] coolGuy;
		try {
			coolGuy = RestoreMonitor.getBlockUnpackaged(sex, adp, p.toString(), siteId, sex.blockSizeBytes, false);
		} catch (IOException e) {
			throw new CloudException(e);
		}
		blockSize = coolGuy.length;
		if(blockSize < 524288) {
			throw new CloudException("HCL first block is illegal block  of size of " + blockSize);
		}
		diskSize = entryCount * blockSize;
		LOGGER.debug("Disk size will be " + diskSize + ", blockSize will be " + blockSize);		
	}
	
	public EclCacheKey getMyKey() { 
		return myKey;
	}
	
	public int getBlockSize() {
		return blockSize;
	}
	
	public long getDiskHclLength() {
		return diskSize;
	}
	
	public Object getMisc() {
		return misc;
	}
	
	public void setMisc(Object o) {
		this.misc = misc;
	}
	
	public synchronized Print getEntry(int blockNum, CloudAdapter adp) {
		if(blockNum < 0) {
			throw new IllegalArgumentException();
		}
		try {
			if(cache.get(blockNum) == null) {
				LOGGER.trace("Cache miss for block " + blockNum);
				populateCache(blockNum);
			}
			Print printy = cache.get(blockNum);
			return printy;
		} catch(IOException e) {
			throw new CloudException(e);
		}
	}
	
	public String getEclPath() {
		return eclPath;
	}
	
	public void populateCache(int  blockNum) throws IOException {
		EclReader eclReader = new EclReader(eclPath);
		HclCursor hcl = eclReader.createCursor(diskNum, blockNum);
		int x = 0;
		for(; x < prefetch; ++x) {
			if(hcl.hasNext() == false) {
				LOGGER.trace("Found hcl EOF at block offset: " + (x + blockNum));
				break;
			}
			cache.put(x + blockNum, hcl.next());
		}
	}
		
		
}
