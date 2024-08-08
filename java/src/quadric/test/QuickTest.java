package quadric.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import quadric.crypt.CryptUtil;
import quadric.spdb.KurganBlock;
import quadric.spdb.KurganBlock.BlockSettings;
import quadric.util.HclWriterUtil;
import quadric.util.Print;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class QuickTest {
	
	@Rule 
	public TemporaryFolder folder = new TemporaryFolder();
	
	
	
	@Test
	public void testAArepackage() throws Exception {
		BlockSettings sex = new BlockSettings();
		sex.blockSizeBytes = 524288;
		sex.setupAesPassword("8ddcd9aef5c85e47f5c2336022f1ec9bdd3635f3c7f13ea0ecdf365200ab8aac");
		String testMeString = "Hello and welcome to your death 2000";
		byte [] testMeOrig = testMeString.getBytes("US-ASCII");
		String md5Orig = CryptUtil.makeMd5Hash(testMeOrig);
		
		int bs = KurganBlock.generateFlags(false, false);
		KurganBlock blockHead = new KurganBlock(testMeOrig, sex.blockPassword, bs, md5Orig);
		blockHead.calcMd5(sex);
		assertEquals(blockHead.getMd5(), md5Orig);
		dumpBlock(blockHead);
		
		bs = KurganBlock.generateFlags(false, true);
		blockHead = new KurganBlock(testMeOrig, sex.blockPassword, bs, md5Orig);
		blockHead.calcMd5(sex);
		assertEquals(blockHead.getMd5(), md5Orig);
		dumpBlock(blockHead);
		
		bs = KurganBlock.generateFlags(true, false);
		blockHead = new KurganBlock(testMeOrig, sex.blockPassword, bs, md5Orig);
		blockHead.calcMd5(sex);
		assertEquals(blockHead.getMd5(), md5Orig);
		System.out.println(CryptUtil.makeMd5Hash(blockHead.getPayloadStrippedOfHeader()));
		dumpBlock(blockHead);
		
		
		// "Repackage" test
		bs = KurganBlock.generateFlags(true, false);
		blockHead = new KurganBlock(testMeOrig, sex.blockPassword, bs, md5Orig);
		blockHead.calcMd5(sex);
		assertEquals(blockHead.getMd5(), md5Orig);
		
		// Add encyption to the source block
		int oldHeader = blockHead.getBlockHeader();
		String fiver = blockHead.getMd5();
		byte [] payloadSansHeader = blockHead.getPayloadStrippedOfHeader();
		int header = KurganBlock.generateFlags(
							false, 	
							true
							);
		blockHead = new KurganBlock(payloadSansHeader, sex.blockPassword, header, fiver);
		// Manually override
		int newHeader = oldHeader | KurganBlock.ENCRYPT_FLAG_AES; 
		//int newHeader = oldHeader;
		blockHead.setBlockHeader(newHeader);
		dumpBlock(blockHead);
		
		
		// Poop up a lz4
		LZ4Factory factory = LZ4Factory.fastestInstance();
		LZ4Compressor compy = factory.fastCompressor();
		byte [] compy2 = compy.compress(testMeOrig);
		String name = "c:\\temp\\compy";
		try (FileOutputStream fos = new FileOutputStream(name)) {
			fos.write(compy2);
		}
		System.out.println("Rapping completo");
	}
	
	private void dumpBlock(KurganBlock bs) throws Exception {
		String name = "c:\\temp\\" + bs.getMd5() + "_" + bs.getBlockHeader();
		try (FileOutputStream fos = new FileOutputStream(name)) {
			fos.write(bs.getPayload());
		}
	}
	
	@Test
	public void testZZZAes() throws Exception {
		String bs = "1F0C45A42693DB7643EF0D961295F13A290D9C6CD9D743BA9B3DD29DD100BBE9";
		//byte [] bs2 = CryptUtil.hexToBytes(bs);
		//bs = new String(bs2, "US-ASCII");
		byte [] cool = OdsTest.createRandoBlock().second;
		String oldCool = "Hello and welcome to hell";
		String md5 = CryptUtil.makeMd5Hash(oldCool.getBytes("US-ASCII"));
		//byte [] cool = oldCool.getBytes("US-ASCII");
		byte [] sucky = null;
		Stopwatch watchy = new Stopwatch();
		int count = 10000;
		for(int x = 0; x < count; ++x) {
			sucky = CryptUtil.encryptAes(cool, bs, md5);
			// Write out for Phil
			/* try (FileOutputStream fos = new FileOutputStream("c:\\temp\\sucky")) {
				fos.write(sucky);
			}*/
		}
		
		double radly = watchy.getAndReset(TimeUnit.MILLISECONDS) / count;
		System.out.println("Did AES encrypt loop in " + radly);
		for(int x = 0; x < count; ++x) {
			byte [] testy = CryptUtil.decryptAes(sucky, bs, 0, sucky.length, md5);
			String cool2 = new String(testy, "US-ASCII");
			//if(cool2.equals(oldCool) == false) {
			//	assertTrue(false);
			//}
		}
		
		radly = watchy.getAndReset(TimeUnit.MILLISECONDS) / count;
		System.out.println("Did AES decrypt loop in " + radly);
		System.out.println("DUME");
		
		
		
	}


	
	/*@Test
	public void testFileCreateIfExists() throws Exception {
		File coolguy = new File(folder.getRoot().toString() + "/testies");
		assertTrue(coolguy.createNewFile());
		try (OutputStream streamy = Files.newOutputStream(Paths.get(coolguy.getAbsolutePath()), StandardOpenOption.CREATE_NEW)) {
			;
		} catch(IOException ioe) {
			System.out.println(ioe);
		}
	} */
	
	
		
	
	@Test
	public void testAAAAAAAAAAAAAAAABlockSex() throws UnsupportedEncodingException, IOException {
		
		BlockSettings sex = new BlockSettings();
		sex.blockSizeBytes = 524288;
		sex.setupAesPassword("8ddcd9aef5c85e47f5c2336022f1ec9bdd3635f3c7f13ea0ecdf365200ab8aac");
		String testMeString = "Hello and welcome to your death 2";
		byte [] testMeOrig = testMeString.getBytes("US-ASCII");
		String md5Orig = CryptUtil.makeMd5Hash(testMeOrig);
		
		int bs = KurganBlock.generateFlags(true, false);
		KurganBlock blockHead = new KurganBlock(testMeOrig, sex.blockPassword, bs, md5Orig);
		blockHead.calcMd5(sex);
		assertEquals(blockHead.getMd5(), md5Orig);
		
		bs = KurganBlock.generateFlags(true, true);
		
		blockHead = new KurganBlock(testMeOrig, sex.blockPassword, bs, md5Orig);
		blockHead.calcMd5(sex);
		assertEquals(blockHead.getMd5(), md5Orig);
		
		bs = KurganBlock.generateFlags(false, false);
		blockHead = new KurganBlock(testMeOrig, sex.blockPassword, bs, md5Orig);
		blockHead.calcMd5(sex);
		assertEquals(blockHead.getMd5(), md5Orig);
		
		bs = KurganBlock.generateFlags(false, true);
		blockHead = new KurganBlock(testMeOrig, sex.blockPassword, bs, md5Orig);
		blockHead.calcMd5(sex);
		assertEquals(blockHead.getMd5(), md5Orig);
		// Write out for Phil
		try (FileOutputStream fos = new FileOutputStream("c:\\temp\\" + md5Orig)) {
			fos.write(blockHead.getPayload());
		}
			
	}
	
	/*@Test
	public void testHex() {
		try {
			String mySexyHex = "0x0000000000000001";
			long longShlong = 1;
			long myIntWad = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
			
			long loveMe = CryptUtil.hexToLong(mySexyHex);
			assertEquals(loveMe, longShlong);
			String loveString = CryptUtil.longToHex(longShlong);
			assertEquals(loveString, mySexyHex);
			String sexFace = CryptUtil.longToHex(myIntWad);
			long wadMan = CryptUtil.hexToLong(sexFace);
			assertEquals(wadMan, myIntWad);
		} catch(Throwable t) {
			t.printStackTrace();
			throw t;
		}
	}*/
	
	/*@Test
	public void testBin() {
		try {
			Set<Print> sex = new TreeSet<Print>();
			int PRINT_COUNT = 100000;
			for(int x = 0; x < PRINT_COUNT; ++x) {
				byte [] bitez = new Integer(x).toString().getBytes();
				String awesome = CryptUtil.makeMd5Hash(bitez);
				Print p = new Print(awesome);
				sex.add(p);
			}
			
			String pathy = folder.getRoot().toString() + "barfy.hcl";
			HclWriterUtil barfy = new HclWriterUtil(pathy);
			sex.forEach(p -> barfy.writePrint(p));
			barfy.writeCoda();
			int [] wad = { 0};
			sex.forEach(p -> {
				int loc = VaultUtil.binarySearchInHcl(p, pathy);
				if(loc != wad[0]++) {
					fail("Binary search test failed");
				}
			});
			byte [] bitez = "HOLYCOW".getBytes();
			String awesome = CryptUtil.makeMd5Hash(bitez);
			Print p = new Print(awesome);
			int loc = VaultUtil.binarySearchInHcl(p, pathy);
			if(loc != -1) {
				fail("'HOLYCOW' shouldn't be in print list man");
			}
		} catch(Throwable t) {
			t.printStackTrace();
			throw t;
		}
		System.out.println("Holy moly");
	}*/

}
