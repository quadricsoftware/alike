package quadric.vhdx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.RlogCache;
import quadric.blockvaulter.VaultSettings;
import quadric.restore.VirtualDisk;
import quadric.util.AutoStruct;
import quadric.util.Print;
import quadric.util.Stopwatch;
import quadric.vhdx.Interpolator.Interpolate;


/**
 * Allows a disk image to be presented as if it were a VHDX file over FUSE
 *
 */
public class VirtualVhdx extends VirtualDisk {
	private static final Logger LOGGER = LoggerFactory.getLogger( VirtualVhdx.class.getName() );
	private static long REGION_REGION_SIZE = (64 * 1024);
	private static long METADATA_REGION_SIZE = (1024 * 1024);
	private static long ONE_MEG = 1024 * 1024;
	
	private Interpolator poler = new Interpolator("VHDX");
	private int hardCodedKBlockSize;
	private int rlogBlockSize;
	private int sectorSize = 512;
	private int blockSize = (1024 * 1024 * 2);
	private int kBlocksPerBlock;
	private long myBigFatVhdxSize;

	
	public VirtualVhdx(RlogCache cache, long imgSize, Interpolate blockAccessor, int siteId) {
		super(imgSize);
		try {
			setup(cache, blockAccessor, siteId);
		} catch (Exception e) {
			throw new CloudException(e);
		}
	}
	
	@Override
	public int read(byte [] data, long offset, int start, int amt) {
		return poler.read(data, offset, start, amt);
	}
	
	@Override
	public long getFileSize() {
		return myBigFatVhdxSize;
	}
	
	private void setup(RlogCache cache, Interpolate blockAccessor, int siteId) throws Exception {
		hardCodedKBlockSize = 524288;
		rlogBlockSize = cache.getBlockSize();
		
		//int bitmapAreaSize = kBlockSize / sectorSize / 8;
		long junk = ((long) sectorSize) * 8388608L;	
		long chunkRatio = junk / blockSize;
		long largestBlock = imgSize / hardCodedKBlockSize;
		
		// DARK MAGIC BEGINS HERE
		// We know the last chunk in there is going to be a bitmap.
		// So just calculate the chunk number of the largest bitmap and use that.
		// Hooha!
		long nonBmpChunks = getChunkNumforSector(largestBlock * (hardCodedKBlockSize / sectorSize));
		int bmpbatnum = (int) (nonBmpChunks / chunkRatio);
		long maxBatCount = (chunkRatio * (bmpbatnum+1) )+ bmpbatnum;
		// Position is one less than size, therefore....
		++maxBatCount;
		
		// The moment we've all been waiting for
		long batTableSize = new VhdxBatEntry().recordSize() * maxBatCount;
		long batStartZone = 3 * 1024 * 1024;
		long firstDataBlockOffset = batStartZone + batTableSize;
		// First data block MUST be at an offset that makes sense
		while((firstDataBlockOffset % blockSize) != 0) {
			firstDataBlockOffset++;
		}
		LOGGER.debug( "chunkRatio: " + chunkRatio 
				+ " BAT count: " + maxBatCount 
				+ " batTableSize: " + batTableSize 
				+ " firstDataBlockOffset: " + firstDataBlockOffset
				);
		
		
		/////////////////////////////////////// File id
		poler.register(new Interpolator.MemCommand(() -> {
			VhdxFileIdentifier id = new VhdxFileIdentifier();
			String nob = "quadricS";
			byte [] ass;
			try {
				ass = nob.getBytes("UTF-16LE");
			} catch (UnsupportedEncodingException e) {
				throw new CloudException(e);
			}
			byte [] assy = new byte[512];
			System.arraycopy(ass, 0, assy, 0, ass.length);
			id.set2Creator_512(assy);
			return id.store();
		}, 
		() -> { return (long) new VhdxFileIdentifier().recordSize(); } )
		, 0);
		
		////////////////////////////////////////// Headers
		final VhdxHeader imTheFirstHeader = new VhdxHeader();
		Guid guid1 = new Guid();
		guid1.randomize();
		Guid guid2 = new Guid();
		guid2.randomize();
		imTheFirstHeader.set05DataWriteGuid(guid1);
		imTheFirstHeader.set04FileWriteGuid(guid2);
		imTheFirstHeader.set09LogLength(1024 * 1024);
		imTheFirstHeader.set10LogOffset(1024 * 1024);
		imTheFirstHeader.set08Version((short) 1);
		imTheFirstHeader.set03SequenceNumber(10);
		// Create el checksum
		imTheFirstHeader.set02Checksum(imTheFirstHeader.checksum());
		poler.register(new Interpolator.MemCommand(() -> {
			byte [] barfando = imTheFirstHeader.store();
			return barfando;
		}, 
		() -> { return (long) imTheFirstHeader.recordSize(); } ),  
		64 * 1024);
		
		poler.register(new Interpolator.MemCommand(() -> {
			// Make a copy so we can change el sequence numbero
			VhdxHeader header2 = new VhdxHeader(imTheFirstHeader);
			header2.set03SequenceNumber(0);
			header2.set02Checksum(0);
			header2.set02Checksum(header2.checksum());
			return header2.store();
		}, 
		() -> { return (long) imTheFirstHeader.recordSize(); } ),
		128 * 1024);
		
		//////////////////////////////////////////// Regions
		// The region tables are written twice for some inscrutable reason, once to offset 192KB and once to offset 256KB
		Interpolator.MemCommand regioner = new Interpolator.MemCommand(() -> {
			
			VhdxRegionTableHeader header = new VhdxRegionTableHeader();
			VhdxRegionTableEntry entry1 = new VhdxRegionTableEntry();
			VhdxRegionTableEntry entry2 = new VhdxRegionTableEntry();
			header.checksum = 0;
			header.entryCount = 2;
			Guid batGuid = new Guid(KnownGuids.BAT_GUID);
			Guid metaGuid = new Guid(KnownGuids.METADATA_REGION_GUID);
			entry1.set1Guid(batGuid);
			entry1.set2FileOffset(3 * 1024 * 1024);
			entry2.set1Guid(metaGuid);
			entry2.set2FileOffset(2 * 1024 * 1024);
			
			int div = (1024 * 1024);
			long wadee = batTableSize;
			while(wadee % div !=0) wadee++;
			entry1.set3Length((int) wadee);
			entry1.set4Required(1);
			entry2.set4Required(1);
			entry2.set3Length(1024 * 1024);
			byte [] wad;
			// Do this TWICE so we can get the CRC right
			while(true) {
				try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
					bos.write(header.store());
					bos.write(entry1.store());
					bos.write(entry2.store());
					int amtLeft = (int) REGION_REGION_SIZE - bos.size();
					bos.write(new byte[amtLeft]);
					wad = bos.toByteArray();
					if(header.get2Checksum() != 0) {
						break;
					}
					header.set2Checksum(AutoStruct.checksum(wad));
				} catch(IOException ioe) { ;}
			}
			return wad;
		}, 
		() -> { return REGION_REGION_SIZE; } );
		
		poler.register(regioner, 192 * 1024);
		poler.register(regioner, 256 * 1024);
		
		////////////////////////////////////////////// Log area
		poler.register(new Interpolator.MemCommand(() -> {
			return new byte[(int) ONE_MEG];
		}, 
		() -> { return ONE_MEG; } ), 
		(ONE_MEG));
		
		///////////////////////////////////////////// Metadata area
		poler.register(new Interpolator.MemCommand(() -> {
			VhdxMetadataTableHeader header = new VhdxMetadataTableHeader();
			List<VhdxMetadataTableEntry> entries = new ArrayList<VhdxMetadataTableEntry>();
			List<AutoStruct> descriptors = new ArrayList<AutoStruct>();
			
			// File params
			VhdxMetadataTableEntry e1 = new VhdxMetadataTableEntry();
			e1.set1ItemId(new Guid(KnownGuids.FILEPARAMS_GUID));
			VhdxFileParameters params = new VhdxFileParameters();
			e1.set3Length(params.recordSize());
			BitTweaker fb = new BitTweaker(new byte[4]);
			// IsRequired
			fb.insertBits(2, 1, 1);
			e1.set4Bits_4(fb.getBits());
			params.set1BlockSize(blockSize);
			entries.add(e1);
			descriptors.add(params);
			
			// All subsequent params have IsVirtualDisk set to 1
			fb.insertBits(1, 1, 1);
			
			// Virt disk size
			VhdxMetadataTableEntry e2 = new VhdxMetadataTableEntry();
			e2.set1ItemId(new Guid(KnownGuids.VIRTUAL_DISK_SIZE_GUID));
			VhdxVirtualDiskSize params2 = new VhdxVirtualDiskSize();
			e2.set3Length(params2.recordSize());
			e2.set4Bits_4(fb.getBits());
			params2.setSize(imgSize);
			entries.add(e2);
			descriptors.add(params2);
			
			// Page 83
			VhdxMetadataTableEntry e3 = new VhdxMetadataTableEntry();
			e3.set1ItemId(new Guid(KnownGuids.PAGE_83));
			VhdxPage83Data params3  = new VhdxPage83Data();
			e3.set3Length(params3.recordSize());
			e3.set4Bits_4(fb.getBits());
			params3.setPage83(new Guid(KnownGuids.PAGE_83));
			entries.add(e3);
			descriptors.add(params3);
			
			// Logical sectorz
			VhdxMetadataTableEntry e4 = new VhdxMetadataTableEntry();
			e4.set1ItemId(new Guid(KnownGuids.LOGICAL_SECTOR_GUID));
			VhdxIntStruct params4  = new VhdxIntStruct();
			e4.set3Length(params4.recordSize());
			e4.set4Bits_4(fb.getBits());
			params4.setVal(512);
			entries.add(e4);
			descriptors.add(params4);
			
			// Physical sector size
			VhdxMetadataTableEntry e5 = new VhdxMetadataTableEntry();
			e5.set1ItemId(new Guid(KnownGuids.PHYSICAL_SECTOR_GUID));
			e5.set3Length(params4.recordSize());
			e5.set4Bits_4(fb.getBits());
			// Pete and Repeat were on a boat, Pete fell in the water,
			// who was left? Pete and Repeat were on a boat...
			entries.add(e5);
			descriptors.add(params4);
			
		
			// Structs MUST NOT begin before 64KB into the region
			int structsOffset = (64 * 1024);
			int maxPos = 10;
			structsOffset += 32 * maxPos;
			int x = 0;
			// Lay down them offsets
			for(VhdxMetadataTableEntry e: entries) {
				e.set2Offset((x * 32) + structsOffset);
				++x;
			}
			header.set3EntryCount((short) entries.size());
			
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
				bos.write(header.store());
				for(VhdxMetadataTableEntry e: entries) {
					bos.write(e.store());
				}
				// Skip to structs offset
				int amtToSkip = structsOffset - bos.size();
				bos.write(new byte[amtToSkip]);
				for(AutoStruct s : descriptors) {
					bos.write(s.store());
					// God this is stupid
					amtToSkip = 32 - s.recordSize();
					if(amtToSkip > 0) {
						bos.write(new byte[amtToSkip]);
					}
				}
				int amtLeft = (int) METADATA_REGION_SIZE - bos.size();
				bos.write(new byte[amtLeft]);
				return bos.toByteArray();
			} catch(IOException ioe) { ;}
			return null;
		}, 
		() -> { return METADATA_REGION_SIZE; } ), 
		(2 * ONE_MEG));

		
		//////////////////////////////////////////////////  BAT area
		//long batIncrement = (chunkRatio +1) * blockSize;
		kBlocksPerBlock = blockSize / hardCodedKBlockSize;
		BitTweaker allocatedBatBits = new BitTweaker(new byte[8]);
		allocatedBatBits.insertBits(0, 3, VhdxBatEntry.PAYLOAD_BLOCK_FULLY_PRESENT);
		BitTweaker bitmapGuy = new BitTweaker(new byte[8]);
		bitmapGuy.insertBits(0,  3, VhdxBatEntry.SB_BLOCK_PRESENT);
		byte [] blankBytes = new byte[8];
		List<BlockEntry> allocatedBlocks = new ArrayList<BlockEntry>();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		Stopwatch watchy = new Stopwatch();
		//watchy.pause();
		for(int x = 0; x < nonBmpChunks; x+= chunkRatio) {
			Print p = null;
			for(int y = 0; y < chunkRatio; ++y) {
				int myKurganBlockNum = (x + y) * kBlocksPerBlock;
				VhdxBatEntry batty = new VhdxBatEntry();
				boolean isBlank = true;
				for(int z = 0; z < kBlocksPerBlock; ++z) {
					int kBlockNum = myKurganBlockNum + z;
					int multi = rlogBlockSize / hardCodedKBlockSize;
					kBlockNum /= multi;
					//long absDiskImgOffset = ((long) kBlockNum) * ((long) kBlockSize);
					p = cache.getEntry(kBlockNum, adp);
					if(p == null) {
						// EOF
						LOGGER.debug( "VHDX EOF found at kblock " + kBlockNum);
						break;
					}
					if(p.isBlank() == false) {
						isBlank = false;
						break;
					}
				}
				if(isBlank == false) {
					long offset = ((long) allocatedBlocks.size() * (long) blockSize) + firstDataBlockOffset;
					BlockEntry be = new BlockEntry(myKurganBlockNum, offset, false);
					allocatedBlocks.add(be);
					LOGGER.trace("Adding allocatad block starting at kBlock " + myKurganBlockNum + " to BAT list, its block offset will be " + offset);
					BitTweaker fer = new BitTweaker(allocatedBatBits.getBits());
					fer.insertBits(20, 44, (offset / ONE_MEG) );
					batty.setBits_8(fer.getBits());
					bos.write(batty.store());
				} else {
					LOGGER.trace("Skipping several kblocks at " + myKurganBlockNum + " in BAT");
					bos.write(blankBytes);
				}
				if(p == null) {
					LOGGER.trace("No more blocks to write to BAT table at kblock" + myKurganBlockNum);
					// There's no more blocks to write
					break;
				}
			}
			if(p == null) {
				// Don't bother writing a sector bitmap,
				// we're done here.
				break;
			}
			// Write a blank bitmap b/c that's the VHDX "dynamic" format
			bos.write(blankBytes);
			
			
		}
		LOGGER.trace("**********Finished BAT area in " + watchy.getElapsed(TimeUnit.MILLISECONDS));
		myBigFatVhdxSize = firstDataBlockOffset + (((long) allocatedBlocks.size()) * ((long) blockSize));
		LOGGER.debug( "VHDX file size: " + myBigFatVhdxSize 
				+ " BAT start zone: "  + batStartZone 
				+ " Allocated VHDX block count: " + allocatedBlocks.size());
		
		
		// Juts return the BAT as one big cruftshow of memory
		poler.register(new Interpolator.MemCommand(() -> {
			return bos.toByteArray();
		}, 
		() -> { return (long) bos.size(); } ), 
		batStartZone);
		
		
		////////////////////////////////////////////// Blocks area
		
		// Oh snap now it's time to register The Actual Raw Img Data
		// This is a little complicated...we use a temporary interpolator
		// which spans only the area they are reading
		// and knows how to make VHDX offsets to img file offsets
		// by using the BlockEntry array we built up earlier while creating the BAT man
		
		final long vhdxDataRegionSize = myBigFatVhdxSize - firstDataBlockOffset;
		final long firstDataBlockOffsetFinal = firstDataBlockOffset;
		//LOGGER.debug( "VHDX data region is " + vhdxDataRegionSize + " which is " + (vhdxDataRegionSize / kBlockSize + " kBlocks"));
		poler.register(new Interpolator.Interpolate() {

			@Override
			public int read(byte[] data, long offset, int start, int amt) {
				int returnAmt = amt;
				//LOGGER.debug("Inoming offset is " + offset + " and amt is " + amt);
				//Interpolator nested = new Interpolator("2MBlockInterceptor");
				for(long off = offset; amt > 0;) { 
					int allocatedBlockNumber = (int) (off / blockSize); 
					//LOGGER.debug("Going to get allocated block from VHDX block (2MB) num " + allocatedBlockNumber);
					BlockEntry be = allocatedBlocks.get(allocatedBlockNumber);
					if(be == null) {
						throw new CloudException("Attempt to read off end");
					}
					long dataRegionVhdxOffset = ((long) allocatedBlockNumber) * ((long) blockSize);
					long offsetAnotherWay = be.vhdxOffset - firstDataBlockOffsetFinal;
					if(dataRegionVhdxOffset != offsetAnotherWay) {
						throw new IllegalArgumentException("Block drift");
					}
					int offsetIntoBlock = (int) (off - ((long) allocatedBlockNumber * (long) blockSize));
					// The maximum amount they can read is 4 contiguous kblocks, aka the VHDX block size
					// but we need to "window" it by their offset into it
					int readAmt = blockSize - offsetIntoBlock;
					if(readAmt > amt) {
						readAmt = amt;
					}
					long imgOffset = ((long) be.startKBlock) * ((long) hardCodedKBlockSize);
					int bufferOffset = (int) (off - offset) + start;
					//LOGGER.debug("About to read " + readAmt + " from kblock " + be.startKBlock + " into output array offset " + bufferOffset + ", array size is " + data.length);
					int read = blockAccessor.read(data, imgOffset + offsetIntoBlock, bufferOffset, readAmt);
					//LOGGER.debug("Read " + read + " from block source at start block " + be.startKBlock + ", wanted " + readAmt);
					amt -= readAmt;
					off += readAmt;
					
				}
				//LOGGER.debug("DONE with read block loop, returning " + returnAmt);
				return returnAmt;
				
			}

			@Override
			public long regionSize() {
				return vhdxDataRegionSize;
			}
			
		}, firstDataBlockOffset);
		
	}
	
	private long getChunkNumforSector(long sectorNum){
		long pos = sectorNum * sectorSize;
		long vile = (long) blockSize;
		long chunkNumber = pos / vile;	// this is the absolute chunk offset, but doesn't account for the bmp interleaving
		return chunkNumber;
	}
	
	/*
	 * Hole E cruft
	 *
	private byte [] makeBitmap(int startBlock) {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		BitSet bitSex = new BitSet(blockSize * 8);
		int blocksPerBitmap = (blockSize * 8);
		for(int x = 0; x < blocksPerBitmap; ++x) {
			boolean isBlank = true;
			for(int y = 0; y < kBlocksPerBlock; ++y) {
				int blockNum = startBlock + (x * kBlocksPerBlock) + y;
				Print p = cache.getEntry(blockNum, adp);
				if(p != null && p.isBlank() == false) {
					isBlank = false;
				}
				if(isBlank == false) {
					bitSex.set(x);
				}
			}
		}
		return bitSex.toByteArray();
		
	} */

}

class BlockEntry {
	int startKBlock;
	long vhdxOffset;
	boolean isBitmap = false;
	BlockEntry(int s, long offset, boolean i) {
		startKBlock = s;
		isBitmap = i;
		this.vhdxOffset = offset;
	}
}
