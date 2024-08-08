package quadric.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.DataStoreType;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.VaultSettings;
import quadric.crypt.CryptUtil;
import quadric.ods.AmbBlockSource;
import quadric.ods.EclFlags;
import quadric.ods.EclHolder;
import quadric.ods.EclReader;
import quadric.ods.MetaGarbage;
import quadric.ods.Ods;
import quadric.ods.Ods.VaultTx;
import quadric.ods.VmVersion;
import quadric.spdb.ConsistencyException;
import quadric.spdb.KurganBlock;
import quadric.spdb.SpdbException;
import quadric.util.ByteStruct;
import quadric.util.HclCursor;
import quadric.util.HclWriterUtil;
import quadric.util.JobControl;
import quadric.util.Pair;
import quadric.util.PathedGetResult;
import quadric.util.Print;
import quadric.util.VaultUtil;


/**
 * Entrypoint for tests
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OdsTest implements DoubleConsumer {
	private static final Logger LOGGER = LoggerFactory.getLogger( OdsTest.class.getName() );
	public static final int BLOCK_SIZE_BYTES = (1024 * 512);
	public static final int BLOCK_COUNT = 3;
	public static final int DISK_COUNT = 1;
	public static final int VAULT_WRITE_THREAD_COUNT = 2;
	public static final int BINARY_FILE_ENTRY_COUNT = 100;
	public static final int NUM_SIMULT_VAULTERS = 1;
	public static final int DS_CONCURRENCY_LEVEL = 5;
	
	@Rule 
	public TemporaryFolder folder = new TemporaryFolder();
	
	private long last = 0;
	
	
	private void initSettings() throws IOException {
		Map<String,String> mappy = new HashMap<String,String>();
		
		String tmpDir = folder.getRoot().getPath();
		// Create the "dbs" subdir
		folder.newFolder("dbs");
		
		
		// Dummy up some settings
		mappy.put("dataStoreUser", "testface");
		mappy.put("dataStorePass", "testface");
		mappy.put("dataStoreShare", tmpDir);
		mappy.put("blockPath", tmpDir);
		mappy.put("dataStoreType", "" + DataStoreType.unified.getValue());
		mappy.put("basePath", tmpDir);
		mappy.put("blockSize", "" + BLOCK_SIZE_BYTES / 1024);
		mappy.put("paranoid", "true");
		mappy.put("dsConcurrencyLevel", "" + DS_CONCURRENCY_LEVEL);
		// Tip of spdbs to use unit test sizes
		mappy.put("__unitTests", "true");
		VaultSettings.instance().debugInitialize(mappy);
	}
	
	
	public static class Wastoid implements ByteStruct<Wastoid> {
		int val;
		
		@Override
		public int compareTo(Wastoid arg0) {
			return new Integer(val).compareTo(arg0.val);
		}

		@Override
		public void load(byte[] bites) {
			ByteBuffer buffy = ByteBuffer.wrap(bites);
			val = buffy.getInt();
		}

		@Override
		public byte[] store() {
			ByteBuffer buffy = ByteBuffer.allocate(4);
			buffy.putInt(val);
			return buffy.array();
		}

		@Override
		public int recordSize() {
			return 4;
		}
	}
	
	/** This is a decent test, but it requires an input dataset from the "jobs" directory
	 * and it also requires a completely fresh ADS.
	 */
	/*@Test
	public void aa_commitTest() throws Exception {
		try {
			initSettings();
			String testPath = "C:\\test\\jobs\\1000\\15";
			String myInstallId = CryptUtil.makeMd5Hash("THEOWNERAGE".getBytes());
			String tmpCommandFlder = folder.getRoot().getPath() + File.separator + myInstallId + "_1000";
			boolean ok = new File(tmpCommandFlder).mkdir();
			assertTrue(ok);
			String commandFilePath = tmpCommandFlder + File.separator + "kg.cmd";
			String json  = "{ \"command\": \"commit\","  
								+ " \"source\": "
								+ "\"" + JsonUtil.escapeJsonString(testPath) + "\","
								+ " \"site\": " 
								+ "\"" + 0 + "\""
								+ "}";
			try (FileWriter fw = new FileWriter(commandFilePath)) {
				fw.write(json);
			} 
			KurganCommander commander = new KurganCommander();
			commander.init();
			commander.trigger(commandFilePath);
			commander.waitForAllComplete();
		} catch(Exception e) {
			e.printStackTrace();
			fail();
		}
	}*/
	
	
	@Test
	public void a_binarySearchTest() throws Exception {
		forceLogging();
		LOGGER.info("Beginning binary search test");
		File tmpFace = folder.newFile();
		ByteBuffer buffy = ByteBuffer.allocate(4);
		try (BufferedOutputStream bus = new BufferedOutputStream(new FileOutputStream(tmpFace.getPath()))) {
			IntStream.rangeClosed(1, BINARY_FILE_ENTRY_COUNT).forEach(i -> {
				buffy.putInt(i);
				try {
					bus.write(buffy.array());
				} catch(Exception t) {
					throw new CloudException(t);
				}
				buffy.clear();
			});
		}
		// Okay run some tests
		long startTime = System.nanoTime();
		try (FileInputStream fis = new FileInputStream(tmpFace.getPath())) {
			for(int x = 1; x< BINARY_FILE_ENTRY_COUNT; ++x) {
				final int z = x;
				IntStream.rangeClosed(1, BINARY_FILE_ENTRY_COUNT).forEach(i -> {
					Wastoid w = new Wastoid();
					w.val = i;
					int maxRecordOffset = z;
					Wastoid w2 = VaultUtil.binarySearch(w, fis.getChannel(), maxRecordOffset, 0, false);
					if(i < maxRecordOffset && w2 == null) {
						fail();
					}
				});
			}	
		}
		long elapsed = (System.nanoTime() - startTime) / 1000000;
		
		LOGGER.info("Binary search test complete--checked " 
						+ BINARY_FILE_ENTRY_COUNT * BINARY_FILE_ENTRY_COUNT
						+ " tests in " + elapsed + "ms");
	}
	
	/* @Test
	public void z_eclTest() throws Exception {
		VmVersion version = new VmVersion();
		HashMap<Integer, Pair<Print,byte[]>> dataSet = new HashMap<Integer, Pair<Print,byte[]>>();
		HashMap<Print,Integer> lookup = new HashMap<Print,Integer>();
		String path = getTempEcl(version, dataSet, lookup);
		
		verifyEcl(path, version, dataSet);
	}*/
	
	@Test
	public void b_odsTest() throws Exception {
		forceLogging();
		try {
			initSettings();
			// Create the ods
			Ods ds = new Ods(0);
			// Use a dummy owner id
			String myInstallId = CryptUtil.makeMd5Hash("THEOWNERAGE".getBytes());
			JobControl control = new JobControl();
			// Take pownerage and sync up
			ds.setOwner(myInstallId);
			ds.sync(myInstallId, this, control);
			for(int x = 0; x < 100; ++x) {
				doRun(ds, myInstallId);
				// Ok that's over...
				//ds.reconcile(this, control);
				LOGGER.info("Recon complete. Now maintPurge");
				ds.maintPurge(myInstallId, this, control);
				LOGGER.info("Doing another run...");
				doRun(ds, myInstallId);
				//ds.reconcile(this, control);
				LOGGER.info("Recon2 complete. Now maintPurge2");
				ds.maintPurge(myInstallId, this, control);
			}
			LOGGER.info("Test complete!");
		} catch (Throwable t) {
			t.printStackTrace();
			throw t;
		}
	}
	
	@Override
	public void accept(double d) {
		if(d > 100 ) {
			// WTF is this cruft?
			org.junit.Assert.fail();
		}
		long now = System.currentTimeMillis();
		if(now - last > 300) {
			System.out.printf("%.2f", d);
			last = now; 
		}
	}
	
	
	private void doRun(Ods ds, String myInstallId) throws Exception {
		// Launch a bunch of simult vaultiness
		Integer [] vaults = {1,2,3,4,5,6,7,8,9,10};
		VaultUtil.reduce(NUM_SIMULT_VAULTERS, () ->
		Arrays.asList(vaults).parallelStream().forEach(tx -> {
			try {
				doVault(ds, myInstallId);
			} catch(Exception ioe) {
				throw new CloudException(ioe);
			}
		}));
	}
	
	private void doVault(Ods ds, String myInstallId) throws Exception {
		JobControl control  = new JobControl();
		// ok sarge
		CloudAdapter adp = ds.getCloudAdapter();
		VmVersion version = new VmVersion();
		HashMap<Integer, Pair<Print,byte[]>> dataSet = new HashMap<Integer, Pair<Print,byte[]>>();
		HashMap<Print, Integer> lookupTable = new HashMap<Print, Integer>();
		String path = getTempEcl(version, dataSet, lookupTable);
		List<String> ambs = createAmbs(dataSet);
		try (AmbBlockSource source = new AmbBlockSource(ambs, path)) {
			MetaGarbage meta = new MetaGarbage(myInstallId, 0, 0);
			
			List<VaultTx> txs = ds.makeVault(meta, path, source, VAULT_WRITE_THREAD_COUNT, this, control);
			AtomicInteger nuke = new AtomicInteger();
			// Test the awesome
			long startTime = System.nanoTime();
			VaultUtil.reduce(VAULT_WRITE_THREAD_COUNT, () ->
			txs.parallelStream().forEach(tx -> {
				// Scope the VaultTx because it's Closeable
				try (VaultTx tmp = tx) {
					while(true) {
						Print p = tx.vaultNext();
						if(p == null) {
							break;
						}
						// Resolve the block 
						byte [] block = source.getBlock(p);
						// Send it
						VaultUtil.putBlockFromMem(block, block.length, adp, p.toString());
						nuke.incrementAndGet();
						
					}
					// Finish him
					tx.commit(this);
				} catch(IOException ioe) {
					throw new CloudException(ioe);
				}
			})
			);
			long elapsed = (System.nanoTime() - startTime) / 1000000;
			LOGGER.info("Sent " + dataSet.size() + " blocks in " + elapsed + "ms time for " + (dataSet.size() * BLOCK_SIZE_BYTES) * 1000 / elapsed + "bytes/sec" );
			// Make sure we sent everything
			
			assertEquals(nuke.get(), dataSet.size());
		} // end amb source
		
		LOGGER.info("All blocks sent, will now verify ECL...");
		PathedGetResult rez = ds.getEcl(version.getNormalizedUuid(), version.getVersion(), this);
		try (InputStream is = rez.in) {
			// Verify ECL correctness
			verifyEcl(rez.localPath, version, dataSet);
			EclReader reader = new EclReader(rez.localPath);
			HclCursor curses = reader.createGlobalCursor();
			Iterable<Print> iterable = () -> curses;
			Stream<Print> targetStream = StreamSupport.stream(iterable.spliterator(), false);
			targetStream.forEach(p -> {
			//targetStream.parallel().forEach(p -> {
				try {
					verifyBlock(p, adp);
				} catch(Exception e) {
					throw new CloudException(e);
				}
			});
		}
		
		LOGGER.info("All data blocks verified. Now deleting the vault");
		ds.makeDelete(myInstallId, version.getNormalizedUuid(), version.getVersion(), this , control);
		LOGGER.info("Delete complete.");
	}
	
	private List<String> createAmbs(Map<Integer, Pair<Print,byte[]>> dataSet) throws Exception {
		int pos = 0;
		int AMB_COUNT = 8;
		File parent = folder.newFolder();
		Set<String> ambs = new HashSet<String>();
		for(Pair<Print,byte[]> data : dataSet.values() ) {
			int ambNum = pos++ % AMB_COUNT;
			String ambFileName = parent.getPath() + File.separator + "0_0_0_"+ ambNum + ".amb";
			ambs.add(ambFileName);
			try (FileOutputStream fos = new FileOutputStream(ambFileName, true)) {
				ByteBuffer buffy = ByteBuffer.allocate(4);
				buffy.order(ByteOrder.LITTLE_ENDIAN);
				// Size field
				buffy.putInt(data.second.length);
				fos.write(buffy.array());
				// Fingerprint field
				// Account for bitpacking....
				String testface = data.first.toString() + "1234";
				byte [] bites = testface.getBytes("US-ASCII");
				fos.write(bites);
				// Data area
				fos.write(data.second);
				LOGGER.trace("Adding print " + testface.substring(0, 32) + " to AMB " + ambFileName);
			} 
		}
		for(int x = 0; x < AMB_COUNT; ++x) {
			String ambFileName = parent.getPath() + File.separator + "0_0_0_"+ x + ".amb";
			try (FileOutputStream fos = new FileOutputStream(ambFileName, true)) {
				// Slam and jam the coda
				fos.write((byte) 'C');
				fos.write((byte) 'O');
				fos.write((byte) 'D');
				fos.write((byte) 'A');
			}
			
			
		}
		return new ArrayList<String>(ambs);
		
	}
	
	private void verifyBlock(Print p, CloudAdapter adp) throws Exception {
		GetResult rez = null;
		try {
			rez = adp.getBlock(p.toString(), 0);
		} catch(SpdbException speede) {
			if(speede.getMessage().contains("not found")) {
				// Breakpoint here to figure out WTF happened
				rez = adp.getBlock(p.toString(), 0);
			}
		}
		byte [] stuff = new byte[(int) rez.len];
		try (InputStream is = rez.in) {
			is.read(stuff);
		}
		KurganBlock.BlockSettings bs = new KurganBlock.BlockSettings();
		bs.blockPassword = "";
		KurganBlock blockHead = new KurganBlock(stuff, bs, p.toString(), true);
		assertEquals(blockHead.getMd5(), p.toString());
		LOGGER.debug( "Verified a block");
	}
	
	private void verifyEcl(String path, VmVersion version, Map<Integer, Pair<Print,byte[]>> dataSet) throws Exception {
		EclReader eclReader = new EclReader(path);
		// Verify some fields
		EclFlags dummy = new EclFlags();
		VmVersion v2 = eclReader.toVmVersion(dummy);
		assertEquals(v2.getMetaData(), version.getMetaData());
		assertEquals(v2.getVmName(), version.getVmName());
		assertEquals(v2.getNormalizedUuid(), version.getNormalizedUuid());
		assertEquals(v2.getVersion(), version.getVersion());
		assertEquals(v2.getDiskSizes().get(0), version.getDiskSizes().get(0));
		// Verify each cursor
		for(int x = 0; x < DISK_COUNT; ++x) {
			HclCursor cursor = eclReader.createCursor(x);
			int pos = 0;
			while(cursor.hasNext()) {
				Print p = dataSet.get((x * BLOCK_COUNT) + pos).first;
				Print p2 = cursor.next();
				assertEquals(p, p2);
				pos++;
			}
			assertTrue(pos == BLOCK_COUNT);
		}
		// Verify the global cursor
		HclCursor cursor = eclReader.createGlobalCursor();
		int pos = 0;
		while(cursor.hasNext()) {
			Print p = dataSet.get(pos).first;
			assertEquals(cursor.next(), p);
			pos++;
		}
		assertTrue(pos == (BLOCK_COUNT * DISK_COUNT));	
		LOGGER.info("ECL verified");
	}
	
	private String getTempEcl(VmVersion version, Map<Integer, Pair<Print,byte[]>> dataSet, Map<Print, Integer> lookup) throws Exception {
		File tmpFile = folder.newFile();
		version.setVmName("TEST_VM_" + new java.rmi.server.ObjID().toString());
		String uuid = CryptUtil.makeMd5Hash(version.getVmName().getBytes("US-ASCII")); 
		version.setUuid(uuid);
		version.setMetaData("Testface");
		version.setVirtualType(1);
		version.setVersion(System.currentTimeMillis());
		List<Long> sizes = new ArrayList<Long>();
		for(int x = 0; x < DISK_COUNT; ++x) {
			sizes.add( (long)(BLOCK_COUNT * BLOCK_SIZE_BYTES));
		}
		version.setDiskSizes(sizes);
		LOGGER.info("Beginning generation of random ECL...");
		// Create some data
		EclHolder holder = new EclHolder(tmpFile.getPath(), version);
		for(int x = 0; x < DISK_COUNT; ++x) {
			HclWriterUtil writer = holder.addDisk(x, BLOCK_COUNT);
			for(int y = 0; y < BLOCK_COUNT; ++y) {
				Pair<Print, byte[]> blockHead = createRandoBlock();
				int myLoc = (x * BLOCK_COUNT) +y;
				dataSet.put(myLoc, blockHead);
				lookup.put(blockHead.first, myLoc);
				writer.writePrint(dataSet.get(myLoc).first);
			}
			writer.writeCoda();
		}
		LOGGER.info("Random ECL generation complete");
		return tmpFile.getPath();
	}
	
	public static Pair<Print,byte[]> createRandoBlock() throws ConsistencyException {
		KurganBlock.BlockSettings bs = new KurganBlock.BlockSettings();
		bs.blockPassword = "";
		bs.blockSizeBytes = BLOCK_SIZE_BYTES +4;
		ByteBuffer buffy = ByteBuffer.allocate(BLOCK_SIZE_BYTES +4);
		buffy.position(4);
		int multi = 128;
		while(buffy.remaining() >= (Integer.BYTES * multi)) {
			int shorty = (int) (Math.random() * Integer.MAX_VALUE);
			for(int x =0; x < multi; ++x) {
				buffy.putInt(shorty);
			}
		}
		String md5 = CryptUtil.makeMd5Hash(buffy.array());
		KurganBlock kurg = new KurganBlock(buffy.array(), bs, md5, false);
		Pair<Print,byte[]> nicePair = new Pair<Print,byte[]>(new Print(kurg.getMd5()), buffy.array());
		return nicePair;
	}
	
	private void forceLogging() {
		/*Logger log = LogManager.getLogManager().getLogger("");
		log.setLevel(Level.FINER);
		for (Handler h : log.getHandlers()) {
		    h.setLevel(Level.FINER);
		}*/
	}

}
