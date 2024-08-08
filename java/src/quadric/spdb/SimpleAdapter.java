package quadric.spdb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.BlockVaulter;
import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.DataStoreType;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.VaultSettings;
import quadric.crypt.CryptUtil;
import quadric.util.VaultUtil;

/**
 * Just uses flat files with the end of the file for the raw md5 (16 bytes)
 *
 */
public class SimpleAdapter extends CloudAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( SimpleAdapter.class.getName() );
	private static final int FILE_BUFFER_SIZE = 8192;
	private static final int PUT_BLOCK_RETRY_COUNT = 10;
	
	public static final String RESERVED_MD5 = "30303030303030303030303030303030";
	public static final String RESERVED_MD5_LF = "3030303030303030303030303030300A";
	

	private String base;
	private int codaSize = 16;
	private boolean legacyMode;
	private boolean useLocalTemp;
	private AtomicInteger tempPathUniqueCounter = new AtomicInteger();
	
	
	/**
	 * @param basey base path
	 * @param legacyMode if true, disable MD5 at end of file
	 */
	public SimpleAdapter(String basey, boolean legacyMode, boolean useLocalTemp, int siteId) {
		super(siteId);
		this.base = basey;
		this.legacyMode = legacyMode;
		this.useLocalTemp = useLocalTemp;
		if(legacyMode) {
			LOGGER.debug( "Initializing in legacy md5 mode for path " + basey);
			codaSize = 0;
		}
	}
	
	@Override
	public boolean putBlock(String path, InputStream in, long len, String dr5) {
		String tmpPath = null;
		// Goofys does not handle appending to a stream very well, so this should fix it
		if(useLocalTemp) {
			try {
				File scrapper =File.createTempFile("s3io", "put", new File(VaultSettings.instance().getTempPath(len)));
				scrapper.deleteOnExit();
				tmpPath = scrapper.toString();
			} catch (IOException e) {
				throw new CloudException(e);
			} 
		} else {
			tmpPath = getPath(path) + tempPathUniqueCounter.incrementAndGet() + ".tmp";
		}
		boolean hasMoved = false;
		try {
			try (FileOutputStream buf = new FileOutputStream(tmpPath)) {
				DigestInputStream dig = CryptUtil.getRollingMd5(in);
				byte [] buffy = new byte[FILE_BUFFER_SIZE];
				while(true) {
					int chew = dig.read(buffy);
					if(chew == -1) {
						break;
					}
					buf.write(buffy, 0, chew);
				}
				dig.close();
				byte [] extra = dig.getMessageDigest().digest();
				String doctorWho = CryptUtil.bytesToHex(extra);
				if(doctorWho.equals(dr5) == false) {
					throw new CloudException("md5 mismatch");
				}
				if(legacyMode == false) {
					buf.write(extra);
				}
			}
			if(useLocalTemp) {
				// Can't use rename/move in this context, so cp it instead
				String destPath = getPath(path);
				String rez = VaultUtil.ezExec("cp -f $1 $2", tmpPath, destPath);
				if(rez.trim().isEmpty() == false) {
					throw new CloudException("Metadata store to " + destPath + " failed with error " + rez);
				}
			} else {
				// Move from temp location on same FS, hopefully atomically
				String rez = VaultUtil.ezExec("mv -f $1 $2", tmpPath, getPath(path));
				if(rez.trim().isEmpty() == false) {
					throw new CloudException("Metadata store to " + path + " failed with error " + rez);
				}
			}
			hasMoved = true;
			return true;
		} catch(IOException e) {
			throw new CloudException(e);
		} finally {
			// Cleanup tmp poop
			if(hasMoved == false || useLocalTemp) {
				File f = new File(tmpPath);
				if(f.exists()) {
					f.delete();
				}
			}
		}
	}	
		
	

	@Override
	public GetResult getBlock(String path, long max) {
		boolean fullFileRead = true;
		if(max != 0) {
			fullFileRead = false;
		}
		FileInputStream fis = null;
		String actualMd5 = "";
		// Only check md5 for full file reads.
		// If they are only reading a part of the file, we can't do this.
		if(fullFileRead) {
			// 1st pass to the the md5 of the whole thing
			actualMd5  = hardMd5(getPath(path));
		}
		// now it's time to get them their stream
		try {
			fis = new FileInputStream(getPath(path));
			GetResult rez = new GetResult();
			if(legacyMode == false) {
				rez.md5 = obtainMd5(fis, getPath(path));
				if(rez.md5.equals(RESERVED_MD5)  
						|| rez.md5.equals(RESERVED_MD5_LF)) {
					LOGGER.warn("Path " + path + " has reserved (zero) md5, allowing it");
					fullFileRead = false;
				}
				if(fullFileRead) {
					if(actualMd5.equals(rez.md5) == false) {
						fis.close();
						throw new CloudException("MD5 mismatch--file at " + path + " is damaged with MD5 " + rez.md5);
					}
				}
			} else {
				rez.md5 = actualMd5;
			}
			rez.in = fis;
			rez.len = fis.getChannel().size()- codaSize;
			if(rez.len > max && max != 0) {
				rez.len = max;
			}
			return rez;
		} catch(IOException ieo) {
			if(fis != null) {
				try { fis.close(); } catch(IOException t) { throw new CloudException(t); }
			}
			throw new CloudException(ieo);
		}
	}

	@Override
	public boolean stat(String path) {
		return new File(getPath(path)).exists();
	}

	@Override
	public void del(String path) {
		new File(getPath(path)).delete();
		
	}

	@Override
	public String id(String path) {
		
		if(legacyMode) {
			return hardMd5(getPath(path));
		}
		try (FileInputStream fis = new FileInputStream(getPath(path))) {
			return obtainMd5(fis, getPath(path));
		} catch(IOException ieo) {
			throw new CloudException(ieo);
		}
	}
	
	private String obtainMd5(FileInputStream fis, String path) throws IOException {
		FileChannel coco = fis.getChannel();
		// Obtain the md5 at the end
		long pos = coco.size() - 16;
		if(pos < 0) {
			throw new CloudException(path + " does not end in a valid MD5 sequence");
		}
		coco.position(pos);
		byte [] sig = VaultUtil.ezLoad(coco, 16);
		coco.position(0);
		return CryptUtil.bytesToHex(sig);
	}
	
	private String hardMd5(String path) {
		return CryptUtil.md5OfFile(path, 0);
		
	}
	
	private String getPath(String path) {
		return base + "/" + path;
	}

	@Override
	public DataStoreType getType() {
		return DataStoreType.cifs;
	}

}
