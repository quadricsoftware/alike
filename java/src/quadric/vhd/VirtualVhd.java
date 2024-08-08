package quadric.vhd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.RlogCache;
import quadric.blockvaulter.VaultSettings;
import quadric.restore.VirtualDisk;
import quadric.util.Print;
import quadric.vhdx.Guid;
import quadric.vhdx.Interpolator;
import quadric.vhdx.Interpolator.Interpolate;

public class VirtualVhd extends VirtualDisk {
	public static final int VHD_EPOCH_BEGIN = 946684800; 
	private static final Logger LOGGER = LoggerFactory.getLogger( VirtualVhd.class.getName() );
	private static final long VHD_MAX_SIZE_BYTES = 2040L * 1024L * 1024L * 1024L;
	private long vhdSize;
	private Interpolator poler = new Interpolator("VHD");
	private int hardCodedKBlockSize;
	private int rlogBlockSize;
	private int sectorSize = 512;
	private int blockSize;
	private int chunkSize;
	private VhdHeader header = new VhdHeader();
	private VhdFooter footer = new VhdFooter();
	
	private long cachedBlockOffset = -1;
	private byte [] cachedBlock;
	
	public VirtualVhd(RlogCache cache, long imgSize, Interpolate blockAccessor, int siteId) {
		super(imgSize);
		
		try {
			setup(cache, blockAccessor, siteId);
		} catch (Exception e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public long getFileSize() {
		return vhdSize;
	}

	@Override
	public int read(byte[] data, long offset, int start, int amt) {
		return poler.read(data, offset, start, amt);
	}

	
	private void setup(RlogCache cache, Interpolate blockAccessor, int siteId) throws Exception {
		if(imgSize > VHD_MAX_SIZE_BYTES) {
			throw new CloudException("VHD too large");
		}
		//String blockSizeKurgan = VaultSettings.instance().getSettings().get("blockSize");
		hardCodedKBlockSize = 524288;
		rlogBlockSize = cache.getBlockSize();
		blockSize = header.get06BlockSize();
		// Let's rationalize this to hex 600
		int batTableOffset = 1536;
		chunkSize = blockSize + sectorSize;
		
		// Configure footer
		Guid hardcodedGuid = new Guid("a2a2a2a2-a2a2-a2a2-a2a2-a2a2a2a2a2a3", ByteOrder.BIG_ENDIAN);
		footer.set14UniqueId_16(hardcodedGuid.store());
		footer.set04DataOffset(footer.recordSize());
		// Juts set this in order to fill out more crapiola
		footer.set05TimeStamp((int) (System.currentTimeMillis() / 1000)); 
		// Make it a dynamic disk
		footer.set12DiskType(3);
		LOGGER.trace("Setting VHD footer orig and current size to " + imgSize);
		footer.set09OrigSize(imgSize);
		footer.set10CurrentSize(imgSize);
		footer.set08CreateorOs(0x5769326B);
		footer.set13Checksum(footer.onesComplementChecksum());
		LOGGER.trace("Set chesksum for footer to " + Integer.toUnsignedLong(footer.get13Checksum()));
		
		
		
		//long maxVirtualSectors = (imgSize / sectorSize);
		// Make them pay for the bitmap entries
		//long actualBytes = imgSize + ((imgSize / blockSize) * sectorSize);
		//long maxBatEntry = (actualBytes / sectorSize); 
		int maxBatCount = (int) (imgSize / blockSize);
		if(imgSize % blockSize != 0) {
			maxBatCount++;
		}
		
		// Configure header
		header.set03TableOffset(batTableOffset);
		header.set05MaxTableEntries(maxBatCount);
		header.set07Checksum(header.onesComplementChecksum());
		LOGGER.trace("Set chesksum for header to " + Integer.toUnsignedLong(header.get07Checksum()));

		int batTableSize = (int) (4 * maxBatCount); 
		//long offPos = batTableOffset + batTableSize;
		
		
		LOGGER.trace("Done configuring footy/hoodie, footer is size " + footer.recordSize() + " and header is size " + header.recordSize());
		
		// Footer
		poler.register(new Interpolator.MemCommand(() -> {
				return footer.store();
		}, 
				() -> { return (long) footer.recordSize(); } )
				, 0);
		// Header
		poler.register(new Interpolator.MemCommand(() -> {
			return header.store();
		}, 
			() -> { return (long) header.recordSize(); } )
			, footer.recordSize());
		
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
		
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		for(int x = 0; x < maxBatCount; ++x) {
			boolean isBlank = true;
			long imgStart = (((long) x) * (long) blockSize);
			if(imgStart < 0) {
				throw new IllegalArgumentException("imgStart is negative");
			}
			long imgEnd = imgStart + blockSize;
			for(long z = imgStart; z < imgEnd; z += hardCodedKBlockSize) {
				int myBlock = (int) (z / hardCodedKBlockSize);
				if(myBlock < 0) {
					throw new IllegalArgumentException("imgStart is negative");
				}
				int multi = rlogBlockSize / hardCodedKBlockSize;
				myBlock /= multi;
				Print p = cache.getEntry(myBlock, adp);
				if(p == null) {
					// EOF
					LOGGER.debug( "VHD EOF found at kblock " + myBlock);
					break;
				}
				if(p.isBlank() == false) {
					isBlank = false;
					break;
				}
			}
			if(isBlank == false) {
				// Determine their allocated block number
				long myBlock = curAllocatedBlocks++;
				// Multiple their block num by the blocksize
				myBlock *= chunkSize;
				// Add in the base offset
				myBlock += dataOffsetBase;
				// A bat entry is an unsigned int
				// Note that casting it may make it negative,
				// but since the format is unsigned we don't care
				int batEntry = (int) (myBlock / sectorSize);
				// Convert to binary
				ByteBuffer useless = ByteBuffer.allocate(4);
				// VHD is in big-endian
				useless.order(ByteOrder.BIG_ENDIAN);
				useless.putInt(batEntry);
				byte [] batEntryBites = useless.array();
				// Shove that cruft into the bat table
				System.arraycopy(batEntryBites, 0, fattyBombBatty, (4 * x), 4);
			}
		} // End bat FOR
		
		// Register the BAT
		LOGGER.trace("BAT will be at offPos " + batTableOffset + ", will feature " + maxBatCount + " entries, " + curAllocatedBlocks + " of which are allocated");
		LOGGER.trace("Data offset commences at position " + dataOffsetBase + "(sector " + (dataOffsetBase / sectorSize) + ")"); 
		poler.register(new Interpolator.MemCommand(() -> {
			return fattyBombBatty;
		}, 
			() -> { return (long) fattyBombBatty.length; } )
			, batTableOffset);
		
		// Unt now zee block data
		for(int vhdBlockPos = 0; vhdBlockPos < maxBatCount; ++vhdBlockPos) {
			// Determine if this block is allocated
			byte [] awesome = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
			byte [] awesome2 = Arrays.copyOfRange(fattyBombBatty, (vhdBlockPos * 4), (vhdBlockPos*4) +4);
			if(Arrays.equals(awesome, awesome2)) {
				// Block isn't allocated, skip
				LOGGER.trace("Skipping unallocated 2MB block at BAT pos " + vhdBlockPos);
				continue;
			}
			// Determine the block offset from the BAT
			// ...it's an unsigned int32 representing sectors
			ByteBuffer fr = ByteBuffer.wrap(awesome2);
			int f2 = fr.getInt();
			final long blockOffset = (Integer.toUnsignedLong(f2) * sectorSize); 
			// Locate the kurgan block by determining the block position in the image
			final long imgOffsetStart = ((long) vhdBlockPos * (long) blockSize);
			final int vhdBlockPosFinal = vhdBlockPos;
			LOGGER.trace("VHD BAT entry val " + f2 + " points to VHD offset " + blockOffset + " and image offset " + imgOffsetStart);
			poler.register(new Interpolator.MemCommand(() -> {
				LOGGER.trace("Intercepting call at VHD offset " + blockOffset + " for img offset " + imgOffsetStart + ", that's BAT position " + vhdBlockPosFinal);
				byte [] awesomo = getVhdBlock(imgOffsetStart, blockAccessor);
				// Populate dat cruft so the bitmap is fully allocated
				return awesomo;
			}, 
				() -> { return (long) chunkSize; } )
				,  blockOffset );
		}
		
		long footerPos = dataOffsetBase + (((long) curAllocatedBlocks +1) * (long) chunkSize);
		
		// Register the footer
		poler.register(new Interpolator.MemCommand(() -> {
			return footer.store();
		}, 
			() -> { return (long) footer.recordSize(); } )
			, footerPos);
		
		// Set the size of the entire butt
		vhdSize =  footerPos + footer.recordSize();
		LOGGER.debug( "VHD file total size is " + vhdSize);
	}
	
	private synchronized byte [] getVhdBlock(long imgOffsetStart, Interpolate blockAccessor) {
		if(imgOffsetStart == cachedBlockOffset) {
			return cachedBlock;
		}
		cachedBlockOffset = imgOffsetStart;
		cachedBlock = new byte[chunkSize];
		Arrays.fill(cachedBlock, 0, sectorSize, (byte) 0xFF);
		// Now read in the block data into the rest of the chunkster
		int amt = blockAccessor.read(cachedBlock, imgOffsetStart, sectorSize, blockSize);
		if(amt != blockSize) {
			LOGGER.debug( "Possible under/overread of " + amt);
		}
		return cachedBlock;
	}
}
