package quadric.ods;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.DoubleConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.crypt.CryptUtil;
import quadric.spdb.KurganBlock;
import quadric.spdb.KurganBlock.BlockSettings;
import quadric.util.ByteStruct;
import quadric.util.ExternalSort;
import quadric.util.HclWriterUtil;
import quadric.util.JobControl;
import quadric.util.OffsetEntry;
import quadric.util.Print;
import quadric.util.VaultUtil;

public class RlogBlockSource implements BlockSource, Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger( RlogBlockSource.class.getName() );
	
	private String lookupFile;
	private List<String> rlogs;
	private String amalgamRlog;
	private String bdbPath;
	private int recordCount;
	private CheckType checkType;
	private int curBlock = -1;
	private int lastCheck = 1;
	private BlockSettings sourceSex;
	private BlockSettings destSex;
	private boolean differentEncyption = false;
	
	public RlogBlockSource(String bdbPath, List<String> rlogLoc, String encPass) {
		this.bdbPath = bdbPath;
		this.rlogs = rlogLoc;
		
		checkType = VaultSettings.instance().getCheckType();
		
		sourceSex = VaultSettings.instance().makeKurganSets();
		if(encPass.equals("") == false) {
			sourceSex.setupLegacyBlockPassword(encPass);
		}
		
		destSex = VaultSettings.instance().makeKurganSets();
		if(sourceSex.legacyOffsitePassword.isEmpty() == false) {
			LOGGER.info("IMPORT BLOCK SOURCE IS ENCRYPTED USING LEGACY BLOWFISH PROTOCOL. Blocks must be unpackaged during transfer.");
			differentEncyption = true;
		}
		if(destSex.shouldCompressNewBlocks) {
			LOGGER.info("New ADS is configured with AES-256 block encyption. Blocks will be encypted during import process.");
			differentEncyption = true;
		}
	}
	
	@Override
	public byte[] getBlock(Print p) {
		boolean shouldCheck = false;
		curBlock++;
		if(	(checkType == CheckType.full) 
				|| (curBlock == 0)
				|| (checkType == CheckType.quadradic && curBlock == (lastCheck * lastCheck * lastCheck * lastCheck))
				) {
			lastCheck++;
			shouldCheck = true;
		}
		OffsetEntry lookup = new OffsetEntry();
		lookup.print = p.toString().toLowerCase();
		try {
			// Obtain rlog offset
			try (FileChannel ch = FileChannel.open(Paths.get(lookupFile), StandardOpenOption.READ)) {
				lookup = VaultUtil.binarySearch(lookup, ch, (int) (ch.size() / (OffsetEntry.RECORD_SIZE +1)), 0, true);
				if(lookup == null) {
					LOGGER.debug("Unable to load block offset for print " + p);
				}
			}
			// Load rlog entry
			RlogEntry re = new RlogEntry();
			try (FileChannel ch = FileChannel.open(Paths.get(amalgamRlog))) {
				ch.position(lookup.offset * RlogEntry.RLOG_ENTRY_SIZE);
				byte [] cool = VaultUtil.ezLoad(ch, RlogEntry.RLOG_ENTRY_SIZE);
				if(cool == null) {
					throw new CloudException("Unexpected rlog EOF at rlog position " + lookup.offset);
				}
				re.load(cool);
			}
			// Load and return block
			byte [] cool = getBlockFromBdb(re);
			if(differentEncyption) {
				// Transform the source block
				KurganBlock blck = new KurganBlock(cool, cool.length, p.toString());
				byte [] radly = blck.unpackage(sourceSex);
				int header = KurganBlock.generateFlags(
									destSex.shouldCompressNewBlocks, 		// compress
									(destSex.blockPassword.isEmpty() == false) // encypt
									);
				blck = new KurganBlock(radly, destSex.blockPassword, header, p.toString());
				cool = blck.getPayload();
			}
			if(shouldCheck) {
				KurganBlock blck = new KurganBlock(cool, destSex, p.toString(), true);
				if(blck.getMd5().equals(p.toString()) == false) {
					throw new CloudException("MD5 mismatch");
				}
			}
			return cool;
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}

	}

	@Override
	public void load(HclWriterUtil neededPrints, DoubleConsumer progress, JobControl c) {
		String lookupFileScratch = null;
		try {
			long totalSize = 0;
			for(String guy : rlogs) {
				totalSize += new File(guy).length();
			}
			amalgamRlog = File.createTempFile("cat", "rlog", new File(VaultSettings.instance().getTempPath(totalSize))).toString();
			LOGGER.debug("Amalgamating " + rlogs.size() + " rlogs to " + amalgamRlog);
			for(String guy : rlogs) {
				VaultUtil.fileAppend(guy, amalgamRlog);
			}
			progress.accept(25);
			File scrapper = File.createTempFile("lookup", "rlog", new File(VaultSettings.instance().getTempPath(totalSize)));
			scrapper.deleteOnExit();
			lookupFile = scrapper.toString();
			
			lookupFileScratch = File.createTempFile("scratch", "rlog").toString();
			long sz =  new File(amalgamRlog).length() / RlogEntry.RLOG_ENTRY_SIZE;
			LOGGER.debug("Generating lookup file at " + lookupFile + " for amalgam rlog with " + sz + " entries");
		
			byte [] foo = new byte[RlogEntry.RLOG_ENTRY_SIZE];
			try (FileInputStream fis = new FileInputStream(amalgamRlog); 
					Writer out = new BufferedWriter(new FileWriter(lookupFileScratch))) {
				while(true) {
					RlogEntry re = new RlogEntry();
					if(VaultUtil.ezLoad(fis, foo, RlogEntry.RLOG_ENTRY_SIZE) == false) {
						LOGGER.debug("Found end of rlog at offset " + recordCount);
						break;
					}
					re.load(foo);
					String cool = new String(re.print, "US-ASCII").toLowerCase();
					OffsetEntry oe = new OffsetEntry();
					oe.offset = recordCount++;
					oe.print = cool.substring(0, 32);
					out.append(oe.toString());
					out.append("\n");
				}
			} 
			progress.accept(50);
			// Now sort
			long lsz =  new File(lookupFileScratch).length() / (OffsetEntry.RECORD_SIZE  +1);
			LOGGER.debug("Sorting lookup file " + lookupFileScratch + " with " + lsz + " entries");
			ExternalSort.SkipBytes skippy = new ExternalSort.SkipBytes();
			ExternalSort.sort(new File(lookupFileScratch), new File(lookupFile), skippy);
			long lsz2 =  new File(lookupFile).length() / (OffsetEntry.RECORD_SIZE  +1);
			progress.accept(100);
			LOGGER.info("DONE rlog block source generation. Rlog contains " + lsz2 + " entries.");
		} catch (IOException e) {
			throw new CloudException(e);
		} finally {
			if(lookupFileScratch != null) {
				new File(lookupFileScratch).delete();
			}
		}
		
	}

	@Override
	public int count() {
		return recordCount;
	}

	@Override
	public void close() throws IOException {
		LOGGER.debug("Cleaning up rlog temp files at " + lookupFile);
		if(lookupFile != null) {
			new File(lookupFile).delete();
		}
		if(amalgamRlog != null) {
			new File(amalgamRlog).delete();
		}
		
	}
	
	private byte [] getBlockFromBdb(RlogEntry re) throws IOException {	
		String bdbFilePath = resolve(re.file);
		try (FileChannel ch = FileChannel.open(Paths.get(bdbFilePath))) {
			ch.position(re.offset);
			return VaultUtil.ezLoad(ch, re.size);
		}
	}
	
	private String resolve(int bdbFile) {
		StringBuilder sb = new StringBuilder();
		sb.append(bdbPath);
		sb.append(File.separator);
		sb.append(bdbFile);
		sb.append(".bdb");
		return sb.toString();
	}

	@Override
	public String getBlockPath(Print p) {
		return null;
	}

}

class RlogEntry  implements ByteStruct<RlogEntry> {
	public static final int RLOG_ENTRY_SIZE = 57;
	
	byte [] print = new byte [33];
	long position;
	int file;
	long offset;	
	int size;
	
	@Override
	public int compareTo(RlogEntry arg0) {
		try {
			return new String(print, "US-ASCII").compareToIgnoreCase(new String(arg0.print, "US-ASCII"));
		} catch (UnsupportedEncodingException e) {
			throw new CloudException(e);
		}
	
	}
	
	@Override
	public void load(byte[] bites) {
		ByteBuffer buffy = ByteBuffer.wrap(bites);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		buffy.get(print);
		position = buffy.getLong();
		file = buffy.getInt();
		offset = buffy.getLong();
		size = buffy.getInt();
		
	}
	@Override
	public byte[] store() {
		throw new CloudException("Not implemented");
	}
	
	@Override
	public int recordSize() {
		return RLOG_ENTRY_SIZE;
	}
	
	
} 
