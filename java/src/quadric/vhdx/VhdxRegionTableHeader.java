package quadric.vhdx;

import quadric.util.AutoStruct;

public class VhdxRegionTableHeader extends AutoStruct {
	public static final int SIG = 0x69676572;
	
	int signature = SIG;
	int checksum;
	int entryCount;
	int reserved;
	
	public int get1Signature() {
		return signature;
	}
	public void set1Signature(int signature) {
		this.signature = signature;
	}
	public int get2Checksum() {
		return checksum;
	}
	public void set2Checksum(int checksum) {
		this.checksum = checksum;
	}
	public int get3EntryCount() {
		return entryCount;
	}
	public void set3EntryCount(int entryCount) {
		this.entryCount = entryCount;
	}
	public int get4Reserved() {
		return reserved;
	}
	public void set4Reserved(int reserved) {
		this.reserved = reserved;
	}
}
