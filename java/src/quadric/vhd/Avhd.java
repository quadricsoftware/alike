package quadric.vhd;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;

import quadric.blockvaulter.CloudException;
import quadric.vhdx.Guid;


/**
 * A utility class for dealing with one-off AVHDs
 * 
 * NB that guid endianness matters a lot here, because Xen cares about that
 *
 */
public class Avhd {
	
	
	public static void main(String [] stuff) throws Exception {
		if(stuff.length < 3) {
			throw new Exception("quadric.vhd.Avhd guid outputPath length");
		}
		String guid = stuff[0];
		String outputPath = stuff[1];
		String size = stuff[2];
		long sz = Long.parseLong(size);
		Guid g = new Guid(guid, ByteOrder.BIG_ENDIAN);
		Avhd d = new Avhd(g, sz, outputPath);
		System.out.println("Created " + outputPath + " successfully");
		
	}
	
	public Avhd(Guid guid, long imgSize, String outputPath) {
		int sectorSize = 512;
		int blockSize;
		int chunkSize;
		VhdHeader header = new VhdHeader();
		VhdFooter footer = new VhdFooter();
		VhdParent p1 = new VhdParent();
		VhdParent p2 = new VhdParent();
		VhdParent p3 = new VhdParent();
		
		byte [] l1 = null;
		byte [] l2 = null;
		byte [] l3 = null;
		
		try {
			l1 = VhdParent.PLAT_CODE_MACX.getBytes("US-ASCII");
			l2 = VhdParent.PLAT_CODE_W2KU.getBytes("US-ASCII");
			l3 = VhdParent.PLAT_CODE_W2RU.getBytes("US-ASCII");
		} catch(Throwable t) { ; }
		
		p1.set01Locator_4(l1);
		p2.set01Locator_4(l2);
		p3.set01Locator_4(l3);
		
		// Got these lengths out of looking at VHD-tool output. Not sure if correct,
		// but probs?
		p1.set03PlatformDataSize(49);
		p2.set03PlatformDataSize(84);
		p3.set03PlatformDataSize(84);
		
		blockSize = header.get06BlockSize();
		int batTableOffset = 1536;
		chunkSize = blockSize + sectorSize;
		
		// Configure footer
		Guid rando = new Guid();
		rando.randomize();
		footer.set14UniqueId_16(rando.store());
		footer.set04DataOffset(footer.recordSize());
		// Juts set this in order to fill out more crapiola
		footer.set05TimeStamp((int) (System.currentTimeMillis() / 1000)); 
		// Make it a delta disk
		footer.set12DiskType(4);
		footer.set09OrigSize(imgSize);
		footer.set10CurrentSize(imgSize);
		footer.set08CreateorOs(0x5769326B);
		footer.set13Checksum(footer.onesComplementChecksum());
				
		int maxBatCount = (int) (imgSize / blockSize);
		if(imgSize % blockSize != 0) {
			maxBatCount++;
		}
		
		// Header time
		CharsetEncoder encoder = Charset.forName("UTF-16BE").newEncoder();
		CharsetEncoder encoderLE = Charset.forName("UTF-16LE").newEncoder();
		CharsetEncoder encoder8 = Charset.forName("UTF-8").newEncoder();
		
		ByteBuffer utf16F = ByteBuffer.allocate(512);
		String name = guid.toHex(ByteOrder.BIG_ENDIAN);
		StringBuilder sb = new StringBuilder(name);
		sb.insert(8, '-');
		sb.insert(13, '-');
		sb.insert(18, '-');
		sb.insert(23, '-');
		name = sb.toString().toLowerCase() + ".vhd";
		try {
			utf16F.put(encoder.encode(CharBuffer.wrap(name)));
		} catch(Throwable t) { 
			throw new CloudException("Cannot encode path as UTF-16BE");
		}
		
		Guid hardcodedGuid = new Guid("a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a3", ByteOrder.BIG_ENDIAN);
		header.set09ParentTimeStamp((int) (System.currentTimeMillis() / 1000)); 
		header.set08ParentId_16(hardcodedGuid.store());
		header.set11ParentUnicodeName_512(utf16F.array());
		header.set03TableOffset(batTableOffset);
		header.set05MaxTableEntries(maxBatCount);

		int batTableSize = (int) (4 * maxBatCount); 
		//long offPos = batTableOffset + batTableSize;
				
		// BAT
		int curAllocatedBlocks = 0;
		byte [] fattyBombBatty = new byte[batTableSize];
		Arrays.fill(fattyBombBatty, 0, batTableSize, (byte) 0xFF);
		// Determine the base offset at the end of the bat table
		long dataOffsetBase = batTableSize + batTableOffset;
		// Round to the nearest sector, victor
		while(dataOffsetBase % sectorSize != 0) {
			dataOffsetBase++;
		}

		long parentLocStringStart = dataOffsetBase + (((long) curAllocatedBlocks +1) * (long) chunkSize);
		long parentLocStringPos = parentLocStringStart;
		
		// Add some parent locators
		p1.set05PlatformOffset(parentLocStringPos);
		parentLocStringPos += 512;
		p2.set05PlatformOffset(parentLocStringPos);
		parentLocStringPos += 512;
		p3.set05PlatformOffset(parentLocStringPos);
		long footerPos = parentLocStringPos + 512;
		
		ByteBuffer parents = ByteBuffer.allocate(192);
		parents.put(p1.store());
		parents.put(p2.store());
		parents.put(p3.store());
		header.set12ParentLocatorEntries_192(parents.array());
		header.set07Checksum(header.onesComplementChecksum());
		
		// Set up parent locator name data
		String locatorName = "./" + name;
		String macNameStr = "file://" + locatorName;
		byte [] macName = null;
		byte [] winName = null;
		try {
			macName = encoder8.encode(CharBuffer.wrap(macNameStr)).array();
			winName = encoderLE.encode(CharBuffer.wrap(locatorName)).array();
		} catch(Throwable t) { 
			throw new CloudException("Cannot encode paths");
		}
		
		File barfer = new File(outputPath);
		try (FileOutputStream fos = new FileOutputStream(barfer)){
			FileChannel chan = fos.getChannel();
			chan.write(ByteBuffer.wrap(footer.store()));
			chan.write(ByteBuffer.wrap(header.store()));
			chan.position(batTableOffset);
			fos.write(fattyBombBatty);
			// Write parent
			chan.position(parentLocStringStart);
			chan.write(ByteBuffer.wrap(macName));
			chan.position(parentLocStringStart + 512);
			chan.write(ByteBuffer.wrap(winName));
			chan.position(parentLocStringStart + 1024);
			chan.write(ByteBuffer.wrap(winName));
			
			chan.position(footerPos);
			chan.write(ByteBuffer.wrap(footer.store()));
		} catch(Throwable t) {
			throw new CloudException(t);
			
		}
			
		
	}
}
