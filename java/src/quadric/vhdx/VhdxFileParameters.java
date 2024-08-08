package quadric.vhdx;

import quadric.util.AutoStruct;

/**
 *  Bits: // LeaveBlocksAllocated:1 HasParent:1
 *
 */
public class VhdxFileParameters extends AutoStruct {
	private int blockSize;
	private byte [] bits= new byte[4];		
	
	public int get1BlockSize() {
		return blockSize;
	}
	public void set1BlockSize(int blockSize) {
		this.blockSize = blockSize;
	}
	public byte [] get2Bits_4() {
		return bits;
	}
	public void set2Bits_4(byte [] bits) {
		this.bits = bits;
	}
	
}
