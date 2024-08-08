package quadric.vhdx;

import quadric.util.AutoStruct;

public class VhdxRegionTableEntry extends AutoStruct {
	private Guid guid = new Guid();
	private long fileOffset;
	private int length;
	private int required;
	
	public Guid get1Guid() {
		return guid;
	}
	public void set1Guid(Guid guid) {
		this.guid = guid;
	}
	public long get2FileOffset() {
		return fileOffset;
	}
	public void set2FileOffset(long fileOffset) {
		this.fileOffset = fileOffset;
	}
	public int get3Length() {
		return length;
	}
	public void set3Length(int length) {
		this.length = length;
	}
	public int get4Required() {
		return required;
	}
	public void set4Required(int required) {
		this.required = required;
	}
	
}
