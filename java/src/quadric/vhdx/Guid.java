package quadric.vhdx;

import java.io.ByteArrayOutputStream;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.nio.ByteOrder;

import quadric.blockvaulter.CloudException;
import quadric.crypt.CryptUtil;
import quadric.util.AutoStruct;
import quadric.util.ByteStruct;


/**
 * Microsoft GUID uses little-endian ints and shorts for the first 8 bites, and big-endian char [] for
 * the last 8 bites, making for a total cruftshow
 * 
 * BUT sometimes we need little-endian
 *
 */
public class Guid implements ByteStruct<Guid> {
	
	private byte[] crufthead = new byte[16];
	
	
	public Guid() {
		;
	}
	
	public Guid(String hex) {
		this(hex, ByteOrder.LITTLE_ENDIAN);
	}
	
	public Guid(String hex, ByteOrder e) {
		hex = hex.replace("-", "");
		hex = hex.replace("{", "");
		hex = hex.replace("}", "");
		byte [] b1 = CryptUtil.hexToBytes(hex.substring(0, 8));
		byte [] b2 = CryptUtil.hexToBytes(hex.substring(8, 12));
		byte [] b3 = CryptUtil.hexToBytes(hex.substring(12, 16));
		byte [] b4 = CryptUtil.hexToBytes(hex.substring(16, 32));
		if(e == ByteOrder.LITTLE_ENDIAN) {
			reverse(b1);
			reverse(b2);
			reverse(b3);
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			bos.write(b1);bos.write(b2);bos.write(b3);bos.write(b4);
		} catch(Throwable t) { ; }
		crufthead = bos.toByteArray();
				
	}
	
	public void reverse(byte [] array) {
		int i = 0;
	      int j = array.length - 1;
	      byte tmp;
	      while (j > i) {
	          tmp = array[j];
	          array[j] = array[i];
	          array[i] = tmp;
	          j--;
	          i++;
	      }
	}
	
	public void randomize() {
		for(int x =0; x < 16; ++x) {
			crufthead[x] = (byte) ThreadLocalRandom.current().nextInt(-128, 128);
		}
		
	}
	
	
	public String toHex() {
		return toHex(ByteOrder.LITTLE_ENDIAN);
	}
	
	public String toHex(ByteOrder e) {
		byte [] b1 = Arrays.copyOfRange(crufthead, 0, 4);
		byte [] b2 = Arrays.copyOfRange(crufthead, 4, 6);
		byte [] b3 = Arrays.copyOfRange(crufthead, 6, 8);
		byte [] b4 = Arrays.copyOfRange(crufthead, 8, 16);
		if(e == ByteOrder.LITTLE_ENDIAN) {
			reverse(b1);
			reverse(b2);
			reverse(b3);
		}
		
		String s1 = CryptUtil.bytesToHex(b1);
		String s2 = CryptUtil.bytesToHex(b2);
		String s3 = CryptUtil.bytesToHex(b3);
		String s4 = CryptUtil.bytesToHex(b4);
		
		return s1 + s2 + s3 + s4;
	}
	
	public boolean matches(String hex) {
		return new Guid(hex).toHex().equals(this.toHex());
	}

	@Override
	public int compareTo(Guid arg0) {
		return this.toHex().compareTo(arg0.toHex());
	}

	@Override
	public void load(byte[] bites) {
		this.crufthead = bites;
		
	}

	@Override
	public byte[] store() {
		return crufthead;
	}

	@Override
	public int recordSize() {
		return 16;
	}

	
}
