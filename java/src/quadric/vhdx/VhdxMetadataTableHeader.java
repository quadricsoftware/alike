package quadric.vhdx;

import quadric.util.AutoStruct;

public class VhdxMetadataTableHeader extends AutoStruct {
	public static final long SIG = 0x617461646174656DL;
	
	long signature = SIG;
	short reserved;
	short entryCount;
	byte [] reserved2_20 = new byte[20];
	
	public long get1Signature() {
		return signature;
	}
	public void set1Signature(long signature) {
		this.signature = signature;
	}
	public short get2Reserved() {
		return reserved;
	}
	public void set2Reserved(short reserved) {
		this.reserved = reserved;
	}
	public short get3EntryCount() {
		return entryCount;
	}
	public void set3EntryCount(short entryCount) {
		this.entryCount = entryCount;
	}
	public byte[] get4Reserved2_20() {
		return reserved2_20;
	}
	public void set4Reserved2_20(byte[] reserved2) {
		this.reserved2_20 = reserved2;
	}
	
	
}
