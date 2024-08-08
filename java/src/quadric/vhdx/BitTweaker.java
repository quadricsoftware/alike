package quadric.vhdx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;

/**
 * Use this guy to extract bit ranges from longs and integers,
 * thus allowing compatability with the geniuses at Redmond
 * who use bit structs
 *
 */
public class BitTweaker {
	private int myLen;
	private BitSet bits;
	
	
	public BitTweaker() { ; }
	public BitTweaker(byte [] inBites) {
		bits = BitSet.valueOf(inBites);
		myLen = inBites.length;
	}
	

	public void setBits(byte [] stuff) {
		this.bits = BitSet.valueOf(stuff);
		myLen = stuff.length;
	}
	
	public byte[] getBits() {
		byte [] retarded = bits.toByteArray();
		if(retarded.length < myLen) {
			// Not sure about this, but WTF
			byte [] stupid = new byte[myLen];
			System.arraycopy(retarded, 0, stupid, 0, retarded.length);
			retarded = stupid;
		}
		return retarded;
	}
	
	public long extractBits(int start, int len) {
		byte [] awesome = bits.get(start, start + len).toByteArray();
		byte [] crufthead = new byte[Long.BYTES];
		System.arraycopy(awesome, 0, crufthead, 0, awesome.length);
		ByteBuffer buffy = ByteBuffer.wrap(crufthead);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		return buffy.getLong();
	}
	
	public void insertBits(int start, int len, long val) {
		ByteBuffer buffy = ByteBuffer.allocate(Long.BYTES);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		buffy.putLong(val);
		BitSet vile = BitSet.valueOf(buffy.array());
		for(int x = 0; x < len; ++x) {
			bits.set(start + x, vile.get(x));
		}
	}
	
}
