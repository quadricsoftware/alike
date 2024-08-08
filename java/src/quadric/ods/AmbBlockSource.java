package quadric.ods;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.util.HclCursor;
import quadric.util.HclReaderUtil;
import quadric.util.HclWriterUtil;
import quadric.util.JobControl;
import quadric.util.Print;
import quadric.util.VaultUtil;

/**
 * Represents a block source backed by AMBs. Requires initial processing to generate a lookup offset file.
 *
 */
public class AmbBlockSource implements BlockSource, Closeable {
	//private static final Logger LOGGER = LoggerFactory.getLogger( OdsTest.class.getName() );
	private static final Logger LOGGER = LoggerFactory.getLogger( AmbBlockSource.class.getName() );
	private static final int BLOCK_SIZE_SANITY = 1024 * 1024 * 2;
	private static final int BUFFER_MAX_SIZE = 1000;
	private static final int MAX_AMB_THREADS = 10;
	public static final int BLOCKDESC_STRUCT_SIZE = 40;	// This is pretty evil because of bitpacking
	
	BinarySearchFile bin;
	String eclFile;
	List<String> ambFiles;
	long totalAmbSizeBytes = 0;
	long skipBytes;
	String ambOffsetFile;
	String nclPath;
	Map<Integer, byte[]> tempBuffer = new HashMap<Integer, byte[]>();
	Map<Short,String> ambFileMapper = new HashMap<Short,String>();
	

	Set<Integer> mappedRecords = new HashSet<Integer>();
	public long amtSent = 0;
	
	public AmbBlockSource(List<String> ambFiles, String eclFile) {
		this.eclFile = eclFile;
		EclReader reader = new EclReader(eclFile);
		skipBytes = reader.header.hclRegionOffset;
		this.ambFiles = ambFiles;
		try {
			long projectedSize = ((long) reader.createGlobalCursor().count()) * 10L;
			File scrapper = File.createTempFile("temp", "amb", new File(VaultSettings.instance().getTempPath(projectedSize)));
			scrapper.deleteOnExit();
			ambOffsetFile = scrapper.toString();
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		
	}
	
	public int count() {
		return mappedRecords.size();
	}
	
	/**
	 * Creates a sorted list of all unique prints from the ECL and then creates a lookup binary file
	 * from that containing all offsets into the AMB
	 * @param progress
	 * @param c
	 */
	public void load(HclWriterUtil neededPrints, DoubleConsumer progress, JobControl c) {
		nclPath = neededPrints.getPath();
		//VaultUtil.verifyBinaryHclOrder(nclPath);
		totalAmbSizeBytes = ambFiles.stream().mapToLong(s -> new File(s).length()).sum();
		LOGGER.info("Parsing " + totalAmbSizeBytes + " bytes of AMB backup data for commit");
		int threadCount = ambFiles.size();
		if(threadCount > MAX_AMB_THREADS) {
			threadCount = MAX_AMB_THREADS;
		}
		if(threadCount == 0) {
			LOGGER.info("No AMBs to load; all good here");
			return;
		}
		final ArrayList<Exception> messes = new ArrayList<Exception>();
		final DoubleConsumer myNestedGuy = VaultUtil.nestedProgress(progress, 0, 100);
		VaultUtil.reduce(threadCount, () -> 
			ambFiles.parallelStream().forEach(f -> {
				try {
					doLoadAmbMeta(f, bin, myNestedGuy, c);
				} catch(Exception e) {
					messes.add(e);
				}
		}));
		
		if(messes.size() > 0) {
			throw new CloudException(messes.get(0));
		}
		try {
			flush();
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		HclCursor curses = new HclReaderUtil(neededPrints.getPath()).createCursor();
		int hclCount =  curses.count();
		synchronized(this) {
			// Create a brief memory barrier so we know access to mappedRecord collection 
			// represents the latest mutation from all child threads (that are now dead)
		}
		if(mappedRecords.size() != hclCount) {
			throw new CloudException("Found " + mappedRecords.size() + " blocks to vault in AMB, but vaulting requires " + hclCount);
		}
	}
	
	@Override
	public byte[] getBlock(Print p) {
		AmbRecord dumdum = new AmbRecord();
		dumdum.p = p;
		try (FileInputStream fis = new FileInputStream(ambOffsetFile)) {
			FileChannel ch = fis.getChannel();
			int recordCount = (int) (ch.size() / dumdum.recordSize());
			dumdum = VaultUtil.binarySearch(dumdum, ch, recordCount -1, 0, false);
			if(dumdum == null) {
				throw new CloudException("Print " + p + " not found in any AMB");
			}
			if(dumdum.sz > BLOCK_SIZE_SANITY || dumdum.sz <= 0) {
				// Sanity check
				throw new CloudException("AMB " + dumdum.ambFile + " is malformed");
			}
			return loadBlockFromAmb(dumdum);
			
		} catch (IOException ioe) {
			throw new CloudException(ioe);
		}
	}
	
	private byte [] loadBlockFromAmb(AmbRecord tard) {
		// Use FileChannel to avoid Windows kernel buffers?
		String fileName = ambFileMapper.get(tard.ambFile);
		if(fileName == null) {
			throw new CloudException("No such AMB with amb number " + tard.ambFile);
		}
		synchronized(this) {
			amtSent += tard.sz;
		}
		Path path = Paths.get(fileName);
		try (FileChannel chan = FileChannel.open(path, StandardOpenOption.READ)) {
			chan.position(tard.offset);
			return VaultUtil.ezLoad(chan, tard.sz);
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
	}
	
	private void doLoadAmbMeta(String ambLoc, BinarySearchFile bin, DoubleConsumer progress, JobControl control) {
		File testies = new File(ambLoc);
		if(testies.exists() == false) {
			LOGGER.trace("AMB is missing at path " + ambLoc);
			return;
		}
		if(testies.length() == 0) {
			LOGGER.debug( "Skipping (legal) zero-sized AMB");
			return;
		}
		try (FileChannel ch = FileChannel.open(Paths.get(ambLoc), StandardOpenOption.READ)) {
			// AMBs have a coda...account for it
			long totalSize = ch.size() - HclReaderUtil.CODA.length();
			int kurganBlockSz = 0;
			for(long pos = 0; pos != totalSize;) {
				KurganAmbHeader kurgAmb = new KurganAmbHeader();
				ByteBuffer buffy = ByteBuffer.allocate(BLOCKDESC_STRUCT_SIZE);
				boolean ok = VaultUtil.ezLoad(ch, buffy);
				if(ok == false) {
					throw new CloudException("Premature EOF on file " + ambLoc);
				}
				kurgAmb.load(buffy.array());
				
				int recordPos = VaultUtil.binarySearchInHcl(kurgAmb.getPrint(), nclPath);
				buffy.position(0);
				
				if(kurgAmb.getBlockSize() > BLOCK_SIZE_SANITY || kurganBlockSz <= 0) {
					throw new CloudException("AMB is malformed");
				}
				if(recordPos == -1) {
					LOGGER.trace("Print " + kurgAmb.getPrint() + " not needed for vault; skipping");
				} else {
					addBlockInfo(recordPos, buffy.array(), ch.position(), ambLoc, kurgAmb.getPrint());
				}
				// Advance to the next AMB offset
				pos = ch.position() + kurganBlockSz;
				ch.position(pos);
				double prog = ((double) pos / totalSize) * 100.00;
				control.control();
				progress.accept(prog);
			}
			progress.accept(100);
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
	}
	
	private void addBlockInfo(int recordNum, byte [] record, long offset, String myAmb, Print p) throws IOException {
		// job_vm_version_disk
		String [] splitz = myAmb.split("_");
		if(splitz.length < 4) {
			throw new CloudException("AMB has invalid filename: " + myAmb);
		}
		String diskNumberStr = splitz[3].split("\\.")[0];
		short diskNumber = Short.parseShort(diskNumberStr);
		synchronized(this) {
			ambFileMapper.put(diskNumber, myAmb);
		}
		byte [] awesome = new byte [record.length + 8 + 2];
		System.arraycopy(record, 0, awesome, 0, record.length);
		ByteBuffer buffy = ByteBuffer.allocate(8 + 2);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		buffy.putLong(offset);
		buffy.putShort(diskNumber);
		System.arraycopy(buffy.array(), 0, awesome, record.length, buffy.position());
		synchronized(this) {
			if(mappedRecords.add(recordNum) == false) {
				return;
			} 
			tempBuffer.put(recordNum, awesome);
			// Flush out the buffer to disk
			if(tempBuffer.size() > BUFFER_MAX_SIZE) {
				flush();
			}
		}
	}

	private void flush() throws IOException {
		if(tempBuffer.size() == 0) {
			 return;
		}
		try (FileChannel fc = FileChannel.open(Paths.get(ambOffsetFile),	StandardOpenOption.CREATE, 
																			StandardOpenOption.WRITE)) {
			tempBuffer.forEach((o,bites) -> {
				try {
					fc.position(o * bites.length);
					VaultUtil.ezStore(fc, bites);
				} catch(IOException ioe) {
					throw new CloudException(ioe);
				}
			});
		}
		LOGGER.trace("Flushed " + tempBuffer.size() + " AMB records to offset file");
		// Nuke em dead
		tempBuffer.clear();
	
	}
	
	
	@Override
	public void close() throws IOException {
		new File(ambOffsetFile).delete();
	}

	@Override
	public String getBlockPath(Print p) {
		return null;
	}
	

}
