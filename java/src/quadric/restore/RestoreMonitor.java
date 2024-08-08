package quadric.restore;

import java.io.ByteArrayOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.DataStores;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.RlogCache;
import quadric.blockvaulter.VaultSettings;
import quadric.crypt.CryptUtil;
import quadric.fuse.FuseMonitor;
import quadric.ods.EclReader;
import quadric.ods.Ods;
import quadric.ods.VmVersion;
import quadric.spdb.KurganBlock;
import quadric.spdb.KurganBlock.BlockSettings;
import quadric.stats.StatsManager;
import quadric.util.HclCursor;
import quadric.util.HclReaderUtil;
import quadric.util.Print;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;
import quadric.vhd.VirtualVhd;
import quadric.vhdx.Interpolator;
import quadric.vhdx.VirtualVhdx;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

/**
 * Somewhat of a misnomer since this doesn't handle restores as much as all I/O requests to read
 * data off of VM versions via pipes.
 * 
 *
 */
public class RestoreMonitor {
	private static final Logger LOGGER = LoggerFactory.getLogger( RestoreMonitor.class.getName() );
	
	private static final int CACHED_RLOG_TRANSACTIONS_MAX = 200;
	private static final int RLOG_ENTRY_CACHE_SIZE = 20000; 
	private static final int PREFETCH_SIZE = 1000;
	
	
	
	private static final int VM_VERSION_CACHE_WINDOW_SECS = 10;
	
	private static boolean RETARD_TEST_MODE = false;
	private static ArrayList<Long> TARD_DATES = new ArrayList<Long>();
	
	static {
		if(RETARD_TEST_MODE) {
			TARD_DATES.add(System.currentTimeMillis());
			TARD_DATES.add(System.currentTimeMillis() - ( 1000 * 60 * 24));
			TARD_DATES.add(System.currentTimeMillis() - ( 1000 * 60 * 24 * 10));
		}
	}
	
	private Map<EclCacheKey, RlogCache> cache;
	private Map<Print,byte []> blockCache;
	private Map<String,VirtualDisk> vhdxCache;
	private Map<String,VmVersion> recentVersionCache;
	private Map<String,Map<String,VmVersion>> versionMapCache;
	private Map<String, List<String>> dirListCache;
	private Map<String,Stat> statCache;
	private Stopwatch lastAccess = new Stopwatch();
	
	private FuseMonitor daddy;
	private long startupDate;
	private boolean paranoid;
	private String  restoreMountDir;
	private  int blockPrefetchCount;
	private long blockCacheMiss;
	private AtomicLong blockCacheHit = new AtomicLong();
	private Object cacheLock = new Object();
	//private FileChannel fc;
	 
	
	@SuppressWarnings({ "unchecked", "rawtypes", "serial" })
	public RestoreMonitor(FuseMonitor daddy, int numThreads) {
		paranoid = VaultSettings.instance().isParanoid();
		if(paranoid) {
			LOGGER.info("Paranoid mode enabled, all blocks will be checked during restore. SLOW!");
		}
		
		restoreMountDir = VaultSettings.instance().getRestoreMountBase();
		// Try to make it if we can
		if(new File(restoreMountDir).exists() == false) {
			throw new CloudException(restoreMountDir + " must exist");
		}
		this.daddy = daddy;
		this.startupDate = new Date().getTime() / 1000;
		
		cache = new LinkedHashMap(CACHED_RLOG_TRANSACTIONS_MAX +1, .75F, true) {
			public boolean removeEldestEntry(Map.Entry eldest) {
				return size() > CACHED_RLOG_TRANSACTIONS_MAX;
			}
		};
		int blockCacheMax = (int) (numThreads * 4.5);
		int multi = VaultSettings.instance().makeKurganSets().blockSizeBytes / 524288;
		blockCacheMax /= multi;
		if(blockCacheMax < 5) {
			blockCacheMax = 5;
		}
		blockPrefetchCount = 2;
		int blockCacheMax2 = blockCacheMax;
		blockCache = new LinkedHashMap(blockCacheMax +1, .75F, true) {
			public boolean removeEldestEntry(Map.Entry eldest) {
				return size() > blockCacheMax2;
			}
		};
		int vhdxCacheMax = numThreads;
		vhdxCache = new LinkedHashMap(vhdxCacheMax + 1, .75F, true) {
			public boolean removeEldestEntry(Map.Entry eldest) {
				return size() > vhdxCacheMax;
			}
		};
		
		int versionMapCacheMax = numThreads;
		versionMapCache = new LinkedHashMap(versionMapCacheMax + 1, .75F, true) {
			public boolean removeEldestEntry(Map.Entry eldest) {
				return size() > versionMapCacheMax;
			}
		};
		
		int recentVersionsMax = versionMapCacheMax * 5;
		recentVersionCache = new LinkedHashMap(recentVersionsMax + 1, .75F, true) {
			public boolean removeEldestEntry(Map.Entry eldest) {
				return size() > recentVersionsMax;
			}
		};
	
		int dirListMax = 50 * numThreads;
		dirListCache = new LinkedHashMap(dirListMax + 1, .75F, true) {
			public boolean removeEldestEntry(Map.Entry eldest) {
				return size() > dirListMax;
			}
		};
		
		
		int statMax = 500 * numThreads;
		statCache = new LinkedHashMap(statMax + 1, .75F, true) {
			public boolean removeEldestEntry(Map.Entry eldest) {
				return size() > statMax;
			}
		};
		
		StatsManager.instance().register("blockCacheHit", () -> getBlockCacheHits());
		StatsManager.instance().register("blockCacheMiss", () -> getBlockCacheMisses());

	}
	
	public long getBlockCacheHits() {
		return blockCacheHit.get();
	}
	
	public long getBlockCacheMisses() {
		return blockCacheMiss;
	}
	
		
	public void handleFollowLink(RestoreHeader header, String path) throws Exception {
		//LOGGER.trace("Entering...");
		
		String symPath = "/dev/null";
		try {
			if(daddy.isPreshutdown() == true) {
				LOGGER.debug( "We are shut down, returning /dev/null");
			} else {
				symPath = resolveFlrPath(path);
				LOGGER.trace("Symbolic path is: " + symPath);
			}
		} finally {
			byte [] awesome = new byte[0];
			awesome = symPath.getBytes("US-ASCII");
			daddy.shared.writeAll((int) header.pipeId, awesome);
		}
	}
	
	public void handleAttr(RestoreHeader header, String path) throws Exception {
		Stat staty = new Stat();
		staty.type = -1;
		try {
			if(daddy.isPreshutdown() == true) {
				throw new CloudException("Shutdown in progress");
			}
			invalidateCache();
			Stat swap = null;
			// Uuid.list can never be cached, since its size changes too often
			if(path.endsWith("uuid.list") == false) {
				synchronized(statCache) {
					// Make a swap variable to avoid nulling staty, which doesn't bode well in the finally clause
					swap = statCache.get(path);
				}
			}
			if(swap == null) {
				swap = getStat(path);
				// Ok, stat2 is non-null now
				if(path.endsWith("uuid.list") == false) {
					synchronized(statCache) {
						statCache.put(path, swap);
					}
				}
			}
			staty = swap;
		} finally {
			daddy.shared.writeAll((int) header.pipeId, staty.store());
		}
	}
	
	public void handleList(RestoreHeader header, String path) throws Exception {
		//LOGGER.trace("Entering...");
		List<String> results = Collections.emptyList(); 
		try {
			if(daddy.isPreshutdown() == true) {
				LOGGER.trace("We are shut down...");
			}
			results = dirList(path);
		} finally {
			LOGGER.trace("Returning " + results.size() + " directory items");
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			StringRecordOutput stringy = new StringRecordOutput(bos);
			for(String s: results) {
				stringy.sendRecord(s);
			}
			LOGGER.trace("Sending complete()...");
			stringy.complete();
			daddy.shared.writeAll((int) header.pipeId, bos.toByteArray());
		}
	}
	
	private Stat getStat(String path) throws Exception {
		Stat staty = new Stat();
		VmVersionHierarchy arch = new VmVersionHierarchy(path);
		if(verifyPath(path) == false) {
			staty.size = 0;
			staty.type = -1;
			staty.date = 0;
		//} else if(arch.isFlrMount()) {
			//staty.size = followLink(path, false).length();
			//staty.type = 2;
		} else if(arch.isDirectory() || arch.isFlrMount()) {
			staty.size = 4096;
			staty.type = 1;
			staty.date = getArchDate(arch);
		} else {
			staty.type = 0;
			CloudAdapter adp = VaultSettings.instance().getAdapter(arch.getSite());
			staty.size = getSize(path, adp);
			staty.date = getArchDate(arch);
		}
		if(arch.isFlrMount()) {
			// Load things up baby
			resolveFlrPath(path);
		}
		return staty;
	}
	
	
	private long getArchDate(VmVersionHierarchy arch) {
		if(arch.getDepth() > 2) {
			LOGGER.trace("Obtaining date for arch " + arch);
			return Long.parseLong(arch.getVersionFolder());
		} else {
			// Default to the clock at time of clazz init
			return startupDate;
		}
	}
	private long getSize(String path, CloudAdapter adp) throws Exception {
		if(path.endsWith("uuid.list")) {
			LOGGER.trace("Returning uuid file size " + path);
			return getUuidListSize(path);
		} else if(path.endsWith("info.meta")) {
			return getMetaFileSize(path);
		} else if(path.endsWith(".hcl") || path.endsWith(".hca")) {
			LOGGER.trace("Returning HCL/A size for " + path);
			VmVersionHierarchy arch = new VmVersionHierarchy(path);
			VmVersion v = getVmVersion(arch);
			if(v == null) {
				return 0;
			}
			// Divide disk size by num of blocks and then that by the HCL entry size
			return  getHclSize(arch, adp); 
		} else if(path.endsWith(".vhdx") == false && path.endsWith(".vhd") == false) {
			LOGGER.trace("Returning img size for " + path);
			VmVersionHierarchy arch = new VmVersionHierarchy(path);
			VmVersion v = getVmVersion(arch);
			if(v == null) {
				return 0;
			}
			return getDiskSize(arch, adp);

		} else {
			LOGGER.trace("Returning VHD/X size for " + path);
			VirtualDisk dex = getVhdx(path);
			return dex.getFileSize();
		}
		
	}
		
	private long getDiskSize(VmVersionHierarchy arch, CloudAdapter adp) throws ParseException {
		VmVersion v = getVmVersion(arch);
		EclCacheKey key = new EclCacheKey(v.getSiteId(), v.getVaultId(), arch.getDiskNum());
		RlogCache myCache = getRlogCache(adp, key);
		return myCache.getDiskHclLength();
	}
	
	private boolean verifyPath(String path) throws Exception {
		VmVersionHierarchy arch = new VmVersionHierarchy(path);
		if(arch.getDepth() == 0) {
			return true;
		}
		String parent = arch.getParent();
		List<String> siblings = dirList(parent);
		LOGGER.trace("Checking path " + path + " with parent " + parent + "; sibling count is " + siblings.size());
		if(parent.endsWith("/") == false) parent = parent + "/";
		for(String s: siblings) {
			String full = (parent + s);
			//LOGGER.debug( "Checking " + full + " vs " + path);
			if(full.equals(path)) {
				return true;
			}
		}
		return false;
		
		
	}
	
	
	private List<String> dirList(String path) throws Exception {
		List<String> results = null; 
		
		synchronized(cacheLock) {
			 results = dirListCache.get(path);
		
			if(results == null) {
				VmVersionHierarchy arch = new VmVersionHierarchy(path);
				results = new ArrayList<String>();
				
				if(arch.getDepth() == 0) {
					listAllSites(results);
				}
				if(arch.getDepth() == 1) {
					listAllVms(results, arch);
				} else if(arch.getDepth() == 2) {
					listVersions(results, arch);
				} else if(arch.getDepth() == 3) {
					listDisks(results, arch);
				}
			}
		}
		return results;
	}
	
	private void listAllSites(List<String> results) {
		if(RETARD_TEST_MODE) {
			results.add("0");
			results.add("1");
		} else {
			int cnt = DataStores.instance().count();
			for(int x = 0 ; x < cnt; ++x) {
				results.add("" + x);
			}
		}
		
	}
	
	private void listVersions(List<String> results, VmVersionHierarchy arch) {
		if(RETARD_TEST_MODE) {
			for(long t : TARD_DATES) {
				results.add("" + t);
			}
		} else {
			Map<String,VmVersion> mappy = listVersionsMap(arch, true);
			results.addAll(mappy.keySet());
		}
	}
	
	private void listDisks(List<String> results, VmVersionHierarchy arch) throws Exception {
		LOGGER.trace("Entering");
		if(RETARD_TEST_MODE) {
			File awesome = dummyFile("/");
			results.addAll(Arrays.asList(awesome.list()));
		} else {
			Ods ds = DataStores.instance().getOds(arch.getSite());
			Map<String,VmVersion> candidates = listVersionsMap(arch, true);
			VmVersion parent = candidates.get(arch.getVersionFolder());
			VmVersion v = ds.revealHeaderManager().getVersionFromRestoreId(arch.getVmNameQualifier(), parent.getVersion());
			
			List<Long> disks = v.getDiskSizes();
			for(int x = 0; x < disks.size(); ++x) {
				results.add("" + x + ".img");
				results.add("" + x + ".rnd");
				results.add("" + x);
				results.add("" + x + ".vhdx");
				results.add("" + x + ".vhd");
				results.add("" + x + ".hcl");
				results.add("" + x + ".hca");
				// "Prime" the pump on FLR mans
				resolveFlrPath(arch.toString() + "/" + x);
			}
			results.add("info.meta");
		}
	}
	
	private void listAllVms(List<String> results, VmVersionHierarchy arch) {
		if(RETARD_TEST_MODE) {
			results.add(pathEscape("JOE MAMMA VM"));
			results.add(pathEscape("JOE MAMMA VM 2"));
			results.add(pathEscape("JOB MAMMA VM "));
			results.add(pathEscape("JO *MAMEZ VM tres"));
		} else {
			Ods ds = DataStores.instance().getOds(arch.getSite());
			List<VmVersion> versions = ds.revealHeaderManager().listUniqueVms();
			List<String> names = versions.stream().map( 
					vv -> pathEscape(vv.getVmName())
					).collect(Collectors.toList());
			
			List<Long> restoreIds = ds.revealHeaderManager().getRestoreIds(versions);
			for(int x = 0; x < names.size(); ++x) {
				String name = names.get(x);
				name = "" + restoreIds.get(x) + "_" + name; 
				names.set(x, name);
			}
			results.addAll(names);
			results.add("uuid.list");
		}
	}
	
	
	
	
	/**
	 * Synthesizes block data from the DS and returns it to an output pipe specified in the header
	 */
	public void handleRead(RestoreHeader header, String path) throws Exception {
		LOGGER.trace(header.toString());
		byte [] awesome = new byte[0];
		int size = -1;
		try {
			if(daddy.isPreshutdown() == true) {
				LOGGER.trace("We are shut down...");
			}
			if(RETARD_TEST_MODE) {
				awesome = dummyFetch(header, path);
				//LOGGER.debug( new String(awesome, "US-ASCII"));
			} else {
				if(path.endsWith(".vhdx")) {
					awesome = vhdxFetch(header, path);
				} else if(path.endsWith(".vhd")) {
					awesome = vhdxFetch(header, path);
				} else if(path.endsWith(".hcl")){
					awesome = hclFetch(header, path, false);
				} else if(path.endsWith(".hca")){
					awesome = hclFetch(header, path, true);
				} else if(path.endsWith("info.meta")){
					awesome = metaFetch(header, path);
				} else if(path.endsWith("uuid.list")) {
					awesome = uuidFetch(header, path);
				} else if(path.endsWith("rnd")){
					awesome = fetch(header, path, true);
				} else {
					awesome = fetch(header, path, false);
				}
			}
		} finally {
			// ALWAYS write something
			Stopwatch watchy = new Stopwatch();
			daddy.shared.writeAll((int) header.pipeId, awesome);
			//LOGGER.debug("Memset in " + watchy.getElapsed(TimeUnit.MICROSECONDS) + "us");
		} 
		LOGGER.trace("Closing pipe " + header.pipeId + " after writing data length " + size + " for " + header);
	}
	
	private int getUuidListSize(String path) {
		return uuidBarf(path).length();
	}
	
	private byte [] uuidFetch(RestoreHeader header, String path) throws Exception {
		LOGGER.trace("Fetching uuid list info " + header);
		byte [] sourceBuf = uuidBarf(path).getBytes("US-ASCII");
		// Don't read beyond believable bounds
		header.truncateReadIfNeeded(sourceBuf.length);
		byte [] awesomeTwo = new byte[header.length];
		if(header.length == 0) {
			return awesomeTwo;
		}
		System.arraycopy(sourceBuf, (int) header.offset, awesomeTwo, 0, header.length);
		return awesomeTwo;
	}
	
	private String uuidBarf(String path) {
		LOGGER.trace("Getting UUID list for " + path);
		VmVersionHierarchy arch = new VmVersionHierarchy(path);
		Ods ds = DataStores.instance().getOds(arch.getSite());
		List<VmVersion> versions = ds.revealHeaderManager().listUniqueVms();
		List<String> names = new ArrayList<String>();
		listAllVms(names, arch);
		StringBuilder bobThe = new StringBuilder();
		int x = 0;
		for(VmVersion v : versions) {
			bobThe.append(v.getNormalizedUuid());
			bobThe.append("=");
			bobThe.append(names.get(x++));
			bobThe.append("\n");
		}
		return bobThe.toString();
	}
	
	private int getHclSize(VmVersionHierarchy arch, CloudAdapter adp) throws ParseException {
		long diskSize = getDiskSize(arch, adp);
		VmVersion v = getVmVersion(arch);
		EclCacheKey key = new EclCacheKey(v.getSiteId(), v.getVaultId(), arch.getDiskNum());
		RlogCache cachey = getRlogCache(adp, key);
		int wads =  (int) (diskSize / cachey.getBlockSize() * HclReaderUtil.RECORD_SIZE_LONG);
		if(diskSize % cachey.getBlockSize() != 0) { 
			wads += HclReaderUtil.RECORD_SIZE_LONG;
		}
		wads += HclReaderUtil.CODA.length();
		return wads;
	}
	
	private String metaFetchString(String path) throws Exception {
		VmVersionHierarchy arch = new VmVersionHierarchy(path);
		int site = arch.getSite(); 
		CloudAdapter adp = VaultSettings.instance().getAdapter(site);
		VmVersion v = getVmVersion(arch);
		EclCacheKey key = new EclCacheKey(v.getSiteId(), v.getVaultId(), 0);
		if(v == null) {
			// Nothing here anymore, version has been deleted?
			return "";
		}
		RlogCache cachey = getRlogCache(adp, key);
		JsonStringEncoder ency = JsonStringEncoder.getInstance();
		return 
				"{\n" 
				+ "\"blockSize\" : \"" + cachey.getBlockSize() + "\",\n"
				+ "\"name\" : \"" + new String(ency.quoteAsString(v.getVmName())) + "\",\n"
				+ "\"uuid\" : \"" + v.getPlatformStyleUuid() + "\",\n"
				+ "\"timestamp\" : \"" + v.getVersion() + "\",\n"
				+ "\"platformId\" : \"" + v.getVirtualType() + "\",\n"
				+ "\"ecl\" : \"" + key.getEclPath() + "\"\n"
				+ "}";
	}
	
	private int getMetaFileSize(String path) throws Exception {
		return metaFetchString(path).getBytes("US-ASCII").length;
	}
	
	private byte []  metaFetch(RestoreHeader header, String path) throws Exception { 
		byte [] sourceBuf = metaFetchString(path).getBytes("US-ASCII");
		// Don't read beyond believable bounds
		header.truncateReadIfNeeded(sourceBuf.length);
		int readAmt = header.length;
		byte [] awesomeTwo = new byte[readAmt];
		if(readAmt == 0) {
			return awesomeTwo;
		}
		System.arraycopy(sourceBuf, (int) header.offset, awesomeTwo, 0, readAmt);
		return awesomeTwo;

	}
	
	private byte [] hclFetch(RestoreHeader header, String path, boolean killDamagedBlocks) throws Exception {
		LOGGER.trace("Entering");
		Stopwatch watchy = new Stopwatch();
		VmVersionHierarchy arch = new VmVersionHierarchy(path);
		int site = arch.getSite(); 
		CloudAdapter adp = VaultSettings.instance().getAdapter(site);
		VmVersion v = getVmVersion(arch);
		if(v == null) {
			// Nothing here anymore, version has been deleted?
			return new byte[0];
		}		
		EclCacheKey key = new EclCacheKey(v.getSiteId(), v.getVaultId(), arch.getDiskNum());
		//RlogCache cachey = getRlogCache(adp, key);
		String eclPath = key.getEclPath();
		LOGGER.debug("Ecl path is " + eclPath);
		int maxSize = getHclSize(arch, adp);
		header.truncateReadIfNeeded(maxSize);
		if(header.length == 0) {
			return new byte[0];
		}
		
		int printStart = (int) header.offset / HclReaderUtil.RECORD_SIZE_LONG;
		int printCount = header.length / HclReaderUtil.RECORD_SIZE_LONG;
		if(printStart % HclReaderUtil.RECORD_SIZE_LONG != 0) {
			printStart--;
		}
		// Make some spare
		printCount+=3;
		
		EclReader eclReader = new EclReader(eclPath, printCount);
		HclCursor hcl = eclReader.createCursor(key.getDiskNum(), printStart);
		byte [] shorty = hcl.bulk(printCount);
		if(shorty == null) {
			//LOGGER.debug("ECL reader bulk returned null, must be trying to read too far!");
			return new byte[0];
		}
		//LOGGER.debug("Resulting shortPrint window is " + shorty.length);
		byte [] shortPrint =  new byte[16];
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		int shortyCount = shorty.length / HclReaderUtil.RECORD_SIZE;
		BadBlockManager badMan = DataStores.instance().getOds(key.getSiteId()).revealBadBlockManager();
		for(int x = 0; x < shortyCount; ++x) {
			int startPos = x * HclReaderUtil.RECORD_SIZE;
			System.arraycopy(shorty, startPos, shortPrint, 0, HclReaderUtil.RECORD_SIZE -1);
			String longPrint = CryptUtil.bytesToHex(shortPrint).toLowerCase();
			if(killDamagedBlocks) {
				Print p = new Print(shortPrint);
				if(badMan.isDamaged(p)) {
					longPrint = "DAMAGEDAMAGEDAMAGEDAMAGEDAMAGEDA";
				}
			} 
			bos.write(longPrint.getBytes("US-ASCII"));
			bos.write((byte) '\n'); 
		}
		// Always slam in a CODA, usually nobody will read it
		bos.write("CODA".getBytes("US-ASCII"));
		
		
		byte [] window = bos.toByteArray();
		Interpolator poler = new Interpolator("Hcl");
		long windowsOffsetAbsolute = printStart * HclReaderUtil.RECORD_SIZE_LONG;
		//LOGGER.debug("Window offset starts at location " + windowsOffsetAbsolute + " and they want abs offset " + header.offset);
		poler.register(new Interpolator.MemCommand(() -> {
			return window;
		}, () -> { return (long) window.length; }), windowsOffsetAbsolute);
		
		byte [] data = new byte[header.length];
		poler.read(data, header.offset, 0, header.length);
		return data;
	}
	
	private byte [] vhdxFetch(RestoreHeader header, String path) throws Exception {
		LOGGER.trace("Entering");
		
		VirtualDisk dex = getVhdx(path);
		header.truncateReadIfNeeded(dex.getFileSize());
		int amtToRead = header.length;
		byte[] yummy = new byte[amtToRead];
		if(amtToRead == 0) {
			return yummy;
		}
		int read = dex.read(yummy, header.offset, 0, amtToRead);
		if(yummy.length != read) {
			throw new CloudException("Unferfulfilled");
		}
		return yummy;
		
	}
	
	private VirtualDisk getVhdx(String path) throws Exception {
		VirtualDisk dex;
		synchronized(vhdxCache) {
			dex = vhdxCache.get(path);
		}
		if(dex == null) {
			LOGGER.debug( "VHDX cache miss for path " + path);
			final BlockSettings sets = VaultSettings.instance().makeKurganSets();
			String imgPath;
			if(path.endsWith(".vhdx")) {
				imgPath = path.replace(".vhdx", ".img");
			} else {
				imgPath = path.replace(".vhd", ".img");
			}
			VmVersionHierarchy arch = new VmVersionHierarchy(imgPath);
			int site = arch.getSite(); 
			
			CloudAdapter adp = VaultSettings.instance().getAdapter(site);
			VmVersion v = getVmVersion(arch);
			if(v == null) {
				throw new CloudException("Version no longer exists");
			}
			
			EclCacheKey key = new EclCacheKey(v.getSiteId(), v.getVaultId(), arch.getDiskNum());
			RlogCache cachey = getRlogCache(adp, key);
			long diskSize = cachey.getDiskHclLength();
			Interpolator.Interpolate pol = new Interpolator.Interpolate() {
				
				@Override
				public int read(byte[] data, long offset, int start, int amt) {
					try {
						int tots = 0;
						while(amt > 0) {
							int read = loadBlockIntoWindow(sets, v.getSiteId(), cachey, adp, data, amt, start, offset);
							if(read == 0) {
								LOGGER.debug("EOF found loading block for VHD/X at position " + offset);
								break;
							}
							amt -= read;
							LOGGER.trace("Read " + read + " bites, remaining is " + amt);
							offset += read;
							start += read;
							tots += read;
						}
						return tots;
					} catch (IOException e) {
						throw new CloudException(e);
					}
				}

				@Override
				public long regionSize() {
					return diskSize;
				}
			};
			if(path.endsWith(".vhdx")) {
				dex = new VirtualVhdx(cachey, diskSize, pol, site);
			} else {
				dex = new VirtualVhd(cachey, diskSize, pol, site);
			}
			synchronized(vhdxCache) {
				vhdxCache.put(path, dex);
			}
			LOGGER.debug( "Added to virtual disk cache at path " +  path);				
		}
		return dex;
	}
	
	/**
	 * Gets needed blocks specified by the rlog and windows into them  
	 */
	private byte [] fetch(RestoreHeader header, String path, boolean isFlr) throws Exception {
		BlockSettings sets = VaultSettings.instance().makeKurganSets();
		VmVersionHierarchy arch = new VmVersionHierarchy(path);
		int site = arch.getSite(); 
		CloudAdapter adp = VaultSettings.instance().getAdapter(site);
		VmVersion v = getVmVersion(arch);
		if(v == null) {
			// Nothing here anymore, version has been deleted?
			LOGGER.info("Fetch of data failed. VM is no longer present (gone). Returning zero-length byte result for request " + header);
			return new byte[0];
		}
		
		
		EclCacheKey key = new EclCacheKey(v.getSiteId(), v.getVaultId(), arch.getDiskNum());
		if(isFlr) {
			// Update last access on FLR
			FlrMapper.instance().updateAccessTime(arch, key);
		}
		RlogCache myCache = getRlogCache(adp, key);
		long diskSize = myCache.getDiskHclLength();
		// Don't allow them to read beyond believable bounds
		header.truncateReadIfNeeded(diskSize);
		byte [] bistro = new byte[header.length];
		if(header.length == 0) {
			LOGGER.debug("Read length is zero, I guess they want EOF");
			return bistro;
		}
		
		int amtToRead = header.length;
		long absoluteOffset = header.offset;
		while(amtToRead > 0) {
			int offsetIntoBuffer = header.length - amtToRead;
			int amtRead = loadBlockIntoWindow(sets, key.getSiteId(), myCache, adp, bistro, amtToRead, offsetIntoBuffer, absoluteOffset);
			if(amtRead == 0) {
				LOGGER.debug("Found end of HCL, part or whole of read will be whitespaced");
			}
			amtToRead -= amtRead;
			absoluteOffset += amtRead;
		}
		return bistro;
	}
	
	private RlogCache getRlogCache(CloudAdapter adp, EclCacheKey key) {
		RlogCache myCache;
		synchronized(cache) {
			myCache = cache.get(key);
		}
		
		if(myCache == null) {
			String eclName = key.getEclPath();
			LOGGER.trace("ECL cache miss at ECL " + eclName + ". Current ECL cache size is " + cache.size());
			myCache = new RlogCache(adp, eclName, key.getDiskNum(), RLOG_ENTRY_CACHE_SIZE, RLOG_ENTRY_CACHE_SIZE, new MiscState(), key.getSiteId(), key.getFlagId());
			synchronized(cache) {
				cache.put(key, myCache);
			}
		}
		return myCache;
	}
	
	private int loadBlockIntoWindow(BlockSettings sex, int siteId, RlogCache rlogCache, CloudAdapter adp, byte [] dest, int remaining, int offsetIntoDestBuffer, long absoluteOffset) 
	throws IOException {
		
		int blockNum = (int) (absoluteOffset / ((long) rlogCache.getBlockSize()));
		int offsetIntoSourceBlock = (int) (absoluteOffset % ((long) rlogCache.getBlockSize()));
		
		Print entry = rlogCache.getEntry(blockNum, adp);
		if(entry == null) {
			// Can't load block because it doesn't exist
			LOGGER.debug("Unable to find out-of-bounds block at absolute offset " + absoluteOffset);
			return 0;
		}
		
		byte [] myLoad = null;
		try {
			myLoad = getBlock(sex, adp, siteId, entry.toString(), rlogCache.getBlockSize());
		} catch(CloudException ce) {
			LOGGER.debug( "Unable to obtain block from data store at rlog position " + blockNum + " with entry " + entry.toString());
			throw ce;
		}
		if(myLoad.length < rlogCache.getBlockSize()) {
			if( ((MiscState)rlogCache.getMisc()).hasLoggedRunt == false) {
				LOGGER.debug("Found runt block " + entry.toString() + " of size " + myLoad.length 
						+ " and client is requesting " + remaining 
						+ " from block internal offset " + offsetIntoSourceBlock);
				((MiscState)rlogCache.getMisc()).hasLoggedRunt = true;
			}
		}
		int len = myLoad.length;
		len -= offsetIntoSourceBlock;
		if(len <= 0) {
			LOGGER.debug("Length of read in loadBlockIntoWindow would be less than or equal to zero, returning EOF");
			return 0;
		}
		if(len > remaining) {
			// Don't over aggro
			len = remaining;
		}
		
		// Only prefetch if FLR is OFF...otherwise FLR's random access pattern
		// is super-problem
		EclCacheKey k = rlogCache.getMyKey();
		if(FlrMapper.instance().isHardMounted(k) == false) {
			// Do some prefetch if possible
			for(int x = 0 ;x < blockPrefetchCount; ++x) { 
				entry = rlogCache.getEntry(blockNum++, adp);
				if(entry != null) {
					getBlock(sex, adp, siteId, entry.toString(), rlogCache.getBlockSize());
				}
			}
		}
		/*LOGGER.debug(
				"\nBlock num: " + blockNum + "\n"
				+ "Print: " + entry + "\n" 
				+ "Remaining: " + remaining + "\n"
				+ "Absolute offset: " + absoluteOffset + "\n" 
				+ "Source length: " + myLoad.length + "\n" 
				 + "Source offset: " + offsetIntoSourceBlock + "\n"
				 + "Dest length: " + dest.length + "\n"
				 + "Dest offset: " + offsetIntoDestBuffer + "\n"
				 + "Copy length: " + len);*/
		
		System.arraycopy(myLoad, offsetIntoSourceBlock, dest, offsetIntoDestBuffer, len);
		return len;
	}
	
	public static byte [] getBlockUnpackaged(BlockSettings sex, CloudAdapter adp, String print, int siteId, int blockSize, boolean isParanoid) throws IOException {
		byte [] awesome = null;
		try {
			GetResult rez = adp.getBlock(print, 0);
		
			KurganBlock blockHead = KurganBlock.create(rez, sex, print, false);
			awesome = blockHead.unpackage(sex);
		} catch(Throwable t) {
			LOGGER.error("Unable to unpackage block " + print + " for data store " + siteId + " due to error " + t.getMessage());
			DataStores.instance().getOds(siteId).revealBadBlockManager().mark(new Print(print));
			awesome = new byte[blockSize];
		}
		if(isParanoid) {
			// Recheck the block
			if(CryptUtil.makeMd5Hash(awesome).equals(print) == false) {
				throw new CloudException("Paranoid MD5 mismatch on print " + print);
			}
		}
		return awesome;
	}
	
	private byte [] getBlock(BlockSettings sex, CloudAdapter adp, int siteId, String print, int blockSize) throws IOException {
		Print p = new Print(print);
		byte [] chewy = null;
		synchronized(blockCache) {
			chewy = blockCache.get(p);
		}
		
		if(chewy == null) {
			chewy = getBlockUnpackaged(sex, adp, print, siteId, blockSize, paranoid);
			synchronized(blockCache) {
				blockCache.put(p, chewy);
				blockCacheMiss++;
			}
		} else {
			blockCacheHit.getAndIncrement();
		}
		return chewy;
	}
	
	
	
	
	
	/**
	 * Maps human-consumption VM version file names to actual VM versions
	 */
	private Map<String,VmVersion> listVersionsMap(VmVersionHierarchy arch, boolean canInvalidate) {
		if(canInvalidate) {
			invalidateCache();
		}
		Map<String,VmVersion> awesome = null;
		synchronized(cacheLock) {
			 awesome = versionMapCache.get(arch.getVmPath());
		
			if(awesome == null) {
				LOGGER.trace("Cache miss on listVersionMap for " + arch.getVmPath());
				awesome = new HashMap<String,VmVersion>();
				Ods ds = DataStores.instance().getOds(arch.getSite());
				List<VmVersion> versions = ds.revealHeaderManager().listVersionsFromRestoreId(arch.getVmNameQualifier());
				List<String> dates = versions.stream().map( 
						vmv -> ("" + vmv.getVersion() )
						).collect(Collectors.toList());
				for(int x = 0; x < versions.size(); ++x) {
					awesome.put(dates.get(x), versions.get(x));
				}
				versionMapCache.put(arch.getVmPath(), awesome);
			}
		}
		return awesome;
		
	}
	
	private String pathEscape(String s) {
		return s.replaceAll("[^a-zA-Z_0-9]", "_");
	}
	
	private String resolveFlrPath(String path) throws Exception {
		//LOGGER.debug("resolving FLR for path " + path);
		VmVersionHierarchy arch = new VmVersionHierarchy(path);
		VmVersion v = getVmVersion(arch);
		if(v == null) {
			throw new CloudException("Path is invalid for " + arch);
		}
		int diskNum = arch.getDiskNum();
		EclCacheKey key = new EclCacheKey(v.getSiteId(), v.getVaultId(), diskNum);
		LOGGER.trace("About to do the deed...");
		String greatly = FlrMapper.instance().getMountedPath(arch, key);
		LOGGER.trace("followSymLink returning path " + greatly);
		return greatly;
	}
	
	
	/**
	 * Provides efficient (cached) access to a VM version
	 * 
	 */
	private VmVersion getVmVersion(VmVersionHierarchy arch) throws ParseException {
		invalidateCache();
		VmVersion v = null;
		synchronized(recentVersionCache) {
			v = recentVersionCache.get(arch.getVmVersionPath());
		}
		if(v == null) {
			LOGGER.trace("Cache miss for getVmVersion for " + arch.getVmVersionPath());
			Ods ds = DataStores.instance().getOds(arch.getSite());
			Map<String,VmVersion> candidates = listVersionsMap(arch, false);
			VmVersion parent = candidates.get(arch.getVersionFolder());
			if(parent == null) {
				// this is odd
				LOGGER.debug( "Cannot find parent version of " + arch.getVmVersionPath());
				return null;
			}
			v = ds.revealHeaderManager().getVersionFromRestoreId(arch.getVmNameQualifier(), parent.getVersion());
			synchronized(recentVersionCache) {
				recentVersionCache.put(arch.getVmVersionPath(), v);
			}
		}
		return v;
		
	}
	
	
	
	private void invalidateCache() {
		boolean shouldNukeCache = false;
		synchronized(lastAccess) {
			if(lastAccess.getElapsed(TimeUnit.SECONDS) > VM_VERSION_CACHE_WINDOW_SECS) {
				shouldNukeCache = true;
			}
		}
		if(shouldNukeCache == false) {
			return;
		}
		// This needs to be dumped periodically to prevent stale listings
		synchronized(cacheLock) {
			versionMapCache.clear();
			dirListCache.clear();
		}
		// placeholder for now
		lastAccess.reset();
	}
	
	private File dummyFile(String path) {
		String basey = "/tmp/happy";
		basey += "/" + path.substring(path.lastIndexOf("/"));
		return new File(basey);
	}
	
	/**
	 * Use this to simply read off a file in /tmp/happy rather than actually 
	 * do anything with a DS
	 */
	private byte [] dummyFetch(RestoreHeader header, String path) throws IOException {
		File f = dummyFile(path);
		if(f.exists() == true) {
			int len = header.length;
			if(len + header.offset > f.length()) {
				len = (int) (f.length() - header.offset);
			}
			if(len < 0) {
				len = 0;
			}
			byte [] chunkly = new byte[len];
			try (FileInputStream fis = new FileInputStream(f)) {
				fis.skip(header.offset);
				fis.read(chunkly);
			}
			return chunkly;
		}
		return new byte[0]; 
	}
}


class MiscState {
	boolean hasLoggedRunt = false;
}

