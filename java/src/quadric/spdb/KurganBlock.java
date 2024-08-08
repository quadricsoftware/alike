package quadric.spdb;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.jpountz.lz4.*;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.RlogCache;
import quadric.blockvaulter.VaultSettings;
import quadric.crypt.CryptUtil;
import quadric.spdb.KurganBlock.BlockSettings;
import quadric.util.VaultUtil;


public class KurganBlock {
	 
	public static class BlockSettings {
		public boolean shouldCompressNewBlocks;
		public boolean shouldEncyptNewBlocks;
		public int blockSizeBytes;
		public String blockPassword = "";
		public String legacyOffsitePassword = "";
				
		public BlockSettings() { 
			; 
		}
		
		public void setupLegacyBlockPassword(String bs) {
			;
			
		}
		
		public void setupAesPassword(String bs) {
			;
		}
	}
	private static final Logger LOGGER = LoggerFactory.getLogger( KurganBlock.class.getName() );
	public static int COMPRESSED_FLAG_GZ = 1;
	public static int COMPRESSED_FLAG_LZ = 2;
	public static int ENCRYPT_FLAG = 64;
	public static int ENCRYPT_FLAG_AES = 128;
	public static int MAX_DECOMPRESSABLE_SIZE = 16 * 1024 * 1024;
	public static int MIN_DECOMPRESSABLE_SIZE = 262144;
	// This is really arbitrary but must not change once it's set.
	// Basically it means our keys don't "align" with any other system that doesn't have the same ivec.

	
	private byte [] payload;
	private String md5 = "";
	private int size = 0;
	private static volatile int currentMaxDecompSize = MIN_DECOMPRESSABLE_SIZE;
	
	
	public static KurganBlock create(GetResult rez, String md5, boolean forceRecheck) throws IOException {
		return create(rez, VaultSettings.instance().makeKurganSets(), md5, forceRecheck);
	}
	
	public static KurganBlock create(GetResult rez, BlockSettings sex, String md5, boolean forceRecheck) throws IOException {
		byte [] load = new byte[(int) rez.len];
		try (InputStream tard = rez.in) {
			if(VaultUtil.ezLoad(tard, load, load.length) == false) {
				throw new IOException("Premature end of stream--expected " + load.length);
			}
		}
		return new KurganBlock(load, sex, md5, forceRecheck);
	}
	
	public KurganBlock(byte [] bites, BlockSettings sets, String md5, boolean forceRecheck) {
		this(bites, sets, bites.length, md5, forceRecheck);
	}
	
	public KurganBlock(byte [] unpackaged, String blockPass, int youBlockHead, String md5) {
		this.md5 = md5;
		packageBlock(unpackaged, blockPass, youBlockHead);
	}
	
	
	
	/**
	 * Constructs a KurganBlock from a payload whose BlockHeader is already included
	 * @param bites
	 * @param sets
	 */
	public KurganBlock(byte [] bites, BlockSettings sets, int size, String md5, boolean forceRecheck) throws ConsistencyException {
		this.payload = bites;
		this.size = size;
		this.md5 = md5;
		if(forceRecheck) {
			calcMd5(sets);
		}
	}
	
	public KurganBlock(byte [] bites, int len, String md5) {
		this.payload = bites;
		this.setMd5(md5);
		this.size = len;
	}
	
	public static int calcMaxBlockSize(int blockSizeFull) {
		int blocSizeFullMax = LZ4Factory.fastestJavaInstance().fastCompressor().maxCompressedLength(blockSizeFull);
		// Round to nearest sanity
		while(blocSizeFullMax % 512 != 0) {
			blocSizeFullMax++;
		}
		return blocSizeFullMax;
	}
	
	public KurganBlock() {
		this.payload = null;
	}
	
	public static int generateFlags(boolean comp, boolean aes) {
		 int retFlag=0;
		 int type=0;
		 if(comp){ type=2; }	// 1= old zlib, 2= lz4
		 if (aes){  retFlag = type | 16 | 128; }
		 else{ retFlag = type | 16;    }
		 return retFlag;
	}
	
	public int getBlockHeader() {
		
		ByteBuffer buffer = ByteBuffer.wrap(payload, 0, 4);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		return buffer.getInt();
	}
	
	public void setBlockHeader(int newHeader) {
		ByteBuffer buffer = ByteBuffer.wrap(payload, 0, 4);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(newHeader);
		
	}
	
	public byte [] getPayload() {
		return this.payload;
	}
	
	public byte [] getPayloadStrippedOfHeader() {
		return Arrays.copyOfRange(payload, 4, size);
	}
	
	
	void setPayload(byte [] bites, int sz) {
		this.payload = bites;
		this.size = sz;
	}
	
	public int getSize() {
		if(payload == null) {
			return 0;
		}
		return size;
	}
	
	public void calcMd5(BlockSettings sets) throws ConsistencyException {
		byte [] subBytes = unpackage(sets);
		// Get the md5 hash of the binary "load"
		this.setMd5(CryptUtil.makeMd5Hash(subBytes));
		
	}

	public void zeroPayload() {
		if(payload == null) {
			throw new SpdbException("Payload cannot be null");
		}
		byte val = 0;
		Arrays.fill(payload, val);
		
	}
	
	public String toString() {
		return "size: " + getSize() + " md5: " + getMd5();
	}
	
	public void packageBlock(byte [] unpackaged, String blockPass, int youBlockHead) {
		int headerSize = 4;
		ByteBuffer buffy = ByteBuffer.allocate(headerSize);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		buffy.putInt(youBlockHead);
		
		if((youBlockHead & COMPRESSED_FLAG_LZ) != 0) {
			LZ4Factory factory = LZ4Factory.fastestInstance();
			LZ4Compressor compy = factory.fastCompressor();
			unpackaged = compy.compress(unpackaged);
		}
		
		if((youBlockHead & ENCRYPT_FLAG_AES) != 0) {
			unpackaged = null;
		} else if((youBlockHead & ENCRYPT_FLAG) != 0) {
			throw new CloudException("Blowfish no longer supported for new blocks");
		}
		
		
		// Copy over to paydirt
		payload = new byte[unpackaged.length + headerSize];
		System.arraycopy(buffy.array(), 0, payload, 0, headerSize);
		System.arraycopy(unpackaged, 0, payload, headerSize, unpackaged.length);
		this.size = payload.length;
	}
	
	public byte [] unpackage(BlockSettings sets) throws ConsistencyException {
		int header  = getBlockHeader();
		try {
			// Get the subbytes of our actual data
			byte [] nakedBlock = payload;
			int paySizeAdj = size -4;
			int startPos = 4;
			
			if((header & ENCRYPT_FLAG_AES) != 0) {
				byte [] decrypted = null;
				nakedBlock = decrypted;
				// Now that we are "naked" and without a 4-byte header, 
				// start from the beginning of the buffer
				startPos = 0;
				// AES has a 16-byte IVEC too...so ya
				paySizeAdj = decrypted.length;
				
			} else if((header & ENCRYPT_FLAG) != 0) {
				// Use the legacy block password
				byte [] decrypted = CryptUtil.decryptBlowfish(payload, sets.legacyOffsitePassword, 4, paySizeAdj);
				nakedBlock = decrypted;
				// Now that we are "naked" and without a 4-byte header, 
				// start from the beginning of the buffer
				startPos = 0;
				
			}
			if((header & COMPRESSED_FLAG_GZ) != 0) {
				throw new CloudException("GZ compression no longer supported");
			} else if((header & COMPRESSED_FLAG_LZ) != 0) {
				// LZ4
				LZ4Factory factory = LZ4Factory.fastestInstance();
				LZ4SafeDecompressor safey = factory.safeDecompressor();

				// Expand MAX_DECOMPRESS_SIZE if needed
				while(true) {
					try {
						nakedBlock = safey.decompress(nakedBlock, startPos, paySizeAdj, currentMaxDecompSize);
						break;
					} catch(LZ4Exception lze) {
						//LOGGER.error("death", lze);
						if(currentMaxDecompSize > MAX_DECOMPRESSABLE_SIZE) {
							currentMaxDecompSize = MAX_DECOMPRESSABLE_SIZE;
							throw lze;
						}
						// Double or nothing
						currentMaxDecompSize = currentMaxDecompSize *2;
					}
				}
			} else if(startPos != 0){
				// No encryption OR compression was enabled...fancy that
				// We need to just strip off the header and return
				nakedBlock = new byte[paySizeAdj];
				System.arraycopy(payload, 4, nakedBlock, 0, paySizeAdj);
			}
			
			return nakedBlock;
		} catch(LZ4Exception lz4e) {
			throw new ConsistencyException("Unable to decompress block " + md5 + " with header " + header + " of size " + payload.length);
		} catch(Exception e) {
			throw new ConsistencyException("Unable to unpackage block " + md5 + " with header " + header, e);
		}
	}

	public String getMd5() {
		return md5;
	}

	public void setMd5(String md5) {
		this.md5 = md5;
	}
}
