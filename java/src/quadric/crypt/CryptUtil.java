package quadric.crypt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import com.twmacinta.util.MD5;

import quadric.blockvaulter.CloudException;

/**
 * 
 *
 */
public class CryptUtil {
	private static final int FILE_BUFFER_SIZE = 8192;
	//private static final ThreadLocal<MessageDigest> localDigest = new ThreadLocal<MessageDigest>();
	
	/**
	 * Faster master
	 * @param bites
	 * @return
	 */
	public static String makeMd5Hash(byte[] bites) {
		 MD5 md5 = new MD5();
		 md5.Update(bites);
		 return md5.asHex().toUpperCase();
		 
	}
	
	public static String md5OfFile(String path, long sz) {
		try (DigestInputStream dis = CryptUtil.getRollingMd5(new FileInputStream(path))) {
			byte[] chomp = new byte[FILE_BUFFER_SIZE];
			long size = new File(path).length() - 16;
			if(sz > 0) {
				size = sz;
			}
			for(long pos = 0; pos < size;) {
				int amtToRead = FILE_BUFFER_SIZE;
				long remaining = (size - pos);
				if(amtToRead > remaining) {
					amtToRead = (int) remaining;
				}
				int actualRead = dis.read(chomp, 0, amtToRead); 
				if(actualRead == -1) {
					throw new CloudException("EOF unexpected");
				}
				pos+= actualRead;
			}
			return CryptUtil.bytesToHex(dis.getMessageDigest().digest());
		} catch(FileNotFoundException fnfe) {
			throw new CloudException("Object at path " + path + " not found");
		} catch(IOException i) {
			throw new CloudException(i);
		}
	}
	
	public static String intToHexLittleEndian(int h) {
		ByteBuffer buffy = ByteBuffer.allocate(Integer.SIZE/ 8);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		buffy.putInt(h);
		return bytesToHex(buffy.array());
	}
	
	public static String intToHexMsStyle(int h) {
		ByteBuffer buffy = ByteBuffer.allocate(Integer.SIZE/ 8);
		buffy.order(ByteOrder.BIG_ENDIAN);
		buffy.putInt(h);
		String awesome = bytesToHex(buffy.array());
		return "0x" + awesome;
	}
	
	public static String longToHex(long h) {
		ByteBuffer buffy = ByteBuffer.allocate(Long.SIZE / 8);
		buffy.order(ByteOrder.BIG_ENDIAN);
		buffy.putLong(h);
		String awesome = bytesToHex(buffy.array());
		return "0x" + awesome;
	}
	
	
	/**
	 * Reverses a hex series, such as 00001F, and deals with it...
	 * java does hex from most significant digits, not least significant,
	 * so it's all kinda a cruftshow
	 * @param awesome
	 * @return
	 */
	public static long hexToLong(String awesome) {
		byte [] biteMe = hexToBytes(awesome.substring(2));
		ByteBuffer eatAss = ByteBuffer.wrap(biteMe);
		eatAss.order(ByteOrder.BIG_ENDIAN);
		return eatAss.getLong();
		
	}
	
	public static byte [] hexToBytes(String s) {
		//StringBuilder bodyBuilder = new StringBuilder(s);
		//return DatatypeConverter.parseHexBinary(bodyBuilder.reverse().toString());
		return DatatypeConverter.parseHexBinary(s);
	}
	
	public static String bytesToHex(byte [] bites) {
		//String wrongEndian = DatatypeConverter.printHexBinary(bites);
		//return new StringBuilder(wrongEndian).reverse().toString();
		return DatatypeConverter.printHexBinary(bites);
	}
	
	
	public static byte [] encryptAes(byte [] bites, String blockPass, String ivecHex) {
		try {
			if(blockPass.length() != 64) {
				throw new CloudException("Illegal AES hex password length");
			}
			SecretKeySpec k = new SecretKeySpec(hexToBytes(blockPass), "AES");
			
			byte [] ivec = hexToBytes(ivecHex);
			Cipher aesCiph = Cipher.getInstance("AES/CFB/NoPadding");
			aesCiph.init(Cipher.ENCRYPT_MODE, k, new IvParameterSpec(ivec));
			return aesCiph.doFinal(bites);
		} catch(Exception e) {
			throw new CloudException("Encyption failed", e);
		}
	}
	
	
	public static byte [] decryptAes(byte [] bites, String blockPass, int offset, int size, String ivecHex) {
		try {
			if(blockPass.length() != 64) {
				throw new CloudException("Illegal AES hex password length");
			}
			SecretKeySpec k = new SecretKeySpec(hexToBytes(blockPass), "AES");
			// Random IVEC starts at teh front
			byte [] ivec = hexToBytes(ivecHex);
			Cipher aesCiph = Cipher.getInstance("AES/CFB/NoPadding");
			aesCiph.init(Cipher.DECRYPT_MODE, k, new IvParameterSpec(ivec));
			return aesCiph.doFinal(bites, offset, size);
		} catch(Exception e) {
			throw new CloudException("Decyption failed", e);
		}
	}
	
	public static byte [] encryptBlowfish(byte [] bites, String blockPass) {
		
		try {
			SecretKeySpec k = new SecretKeySpec(blockPass.getBytes("US-ASCII"), "Blowfish");
			byte [] ivec = new byte[8];
			Cipher blowCiph = Cipher.getInstance("Blowfish/CFB/NoPadding");
			blowCiph.init(Cipher.ENCRYPT_MODE, k, new IvParameterSpec(ivec));
			return blowCiph.doFinal(bites);
		} catch (Exception e) {
			throw new CloudException(e);
		}
	}

	public static byte [] decryptBlowfish(byte [] bites, String blockPass, int offset, int size) {
		try {
			SecretKeySpec k = new SecretKeySpec(blockPass.getBytes("US-ASCII"), "Blowfish");
			byte [] ivec = new byte[8];
			Cipher blowCiph = Cipher.getInstance("Blowfish/CFB/NoPadding");
			blowCiph.init(Cipher.DECRYPT_MODE, k, new IvParameterSpec(ivec));
			//decrypted = ciph.doFinal(subBites);
			return blowCiph.doFinal(bites, offset, size);
		} catch(Exception e) {
			throw new CloudException("Decyption failed", e);
		}
	}
	
		
	public static DigestOutputStream getRollingMd5(OutputStream orig) {
		try {
			MessageDigest burpy = MessageDigest.getInstance("MD5");
			return new DigestOutputStream(orig, burpy);
		} catch(Exception e) { 
			throw new CloudException(e);
		}
	}
	
	public static DigestInputStream getRollingMd5(InputStream orig) {
		try {
			MessageDigest burpy = MessageDigest.getInstance("MD5");
			return new DigestInputStream(orig, burpy);
		} catch(Exception e) { 
			throw new CloudException(e);
		}
	}
	
	/**
	 * Converts most significant hex to least significant, and then adds in the 0x (Micky style) for fun
	 */
	private static void decToHex(StringBuilder sb, int len) {
		if(len % 2 == 0) {
			sb.insert(0, '0');
		}
		while(sb.length() < len) {
			sb.append('0');
		}
		reverseHex(sb);
		sb.insert(0, "0x");
	}
	
	private static void reverseHex(StringBuilder sb) {
		int len = sb.length();
		if(len % 2 != 0) {
			throw new CloudException("Hex string poorly formed");
		}
		StringBuilder sb2 = new StringBuilder(len);
		for(int x = 0; x < len; x+=2) {
			sb2.insert(0, sb.subSequence(x, x+2));
		}	
		
		sb.replace(0, len, sb2.toString());
	}
	
}
