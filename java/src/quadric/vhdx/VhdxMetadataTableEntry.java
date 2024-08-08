package quadric.vhdx;

import quadric.util.AutoStruct;

/**
 *  Bits: 
 *  IsUser:1 IsVirutalDisk:1 IsRequired:1
 *
 */
public class VhdxMetadataTableEntry extends AutoStruct {
	private Guid itemId = new Guid();
	private int offset;
	private int length;
	private byte[] bits = new byte[4];
	private int reserved2;
	
	public Guid get1ItemId() {
		return itemId;
	}
	public void set1ItemId(Guid itemId) {
		this.itemId = itemId;
	}
	public int get2Offset() {
		return offset;
	}
	public void set2Offset(int offset) {
		this.offset = offset;
	}
	public int get3Length() {
		return length;
	}
	public void set3Length(int length) {
		this.length = length;
	}
	public byte[] get4Bits_4() {
		return bits;
	}
	
	public void set4Bits_4(byte[] bits) {
		if(bits.length != 4) {
			throw new IllegalArgumentException("Byte length must be 4");
		}
		this.bits = bits;
	}
	public int get5Reserved2() {
		return reserved2;
	}
	public void set5Reserved2(int reserved2) {
		this.reserved2 = reserved2;
	}
}
