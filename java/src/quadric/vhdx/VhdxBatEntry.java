package quadric.vhdx;

import java.nio.ByteBuffer;

import quadric.util.AutoStruct;
import quadric.util.ByteStruct;

/**
 * First 3 bits: state
 * Next 17 bits: reserved
 * Final 44 bits: file offset MB
 *
 */
public class VhdxBatEntry implements ByteStruct<VhdxBatEntry> {
	public static final int PAYLOAD_BLOCK_NOT_PRESENT = 0;
	public static final int PAYLOAD_BLOCK_UNDEFINED = 1;
	public static final int PAYLOAD_BLOCK_ZERO = 2;
	public static final int PAYLOAD_BLOCK_UNMAPPED = 3;
	public static final int PAYLOAD_BLOCK_FULLY_PRESENT = 6;
	public static final int PAYLOAD_BLOCK_PARTIALLY_PRESENT = 7;

	public static final int SB_BLOCK_NOT_PRESENT = 0;
	public static final int SB_BLOCK_PRESENT = 6;
	
	private byte [] bits = new byte[8];

	public byte [] getBits_8() {
		return bits;
	}
	
	public void setBits_8(byte [] bits) {
		this.bits = bits;
	}

	@Override
	public int compareTo(VhdxBatEntry arg0) {
		 return -1;
	}

	@Override
	public void load(byte[] bites) {
		this.bits = bites;
		
	}

	@Override
	public byte[] store() {
		return bits;
	}

	@Override
	public int recordSize() {
		return 8;
	}
}
