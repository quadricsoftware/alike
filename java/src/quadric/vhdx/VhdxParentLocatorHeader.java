package quadric.vhdx;

import quadric.util.AutoStruct;

public class VhdxParentLocatorHeader extends AutoStruct {
	private Guid locatorType = new Guid();
	private short reserved;
	private short keyValueCount;
	
	public Guid get1LocatorType() {
		return locatorType;
	}
	public void set1LocatorType(Guid locatorType) {
		this.locatorType = locatorType;
	}
	public short get2Reserved() {
		return reserved;
	}
	public void set2Reserved(short reserved) {
		this.reserved = reserved;
	}
	public short get3KeyValueCount() {
		return keyValueCount;
	}
	public void set3KeyValueCount(short keyValueCount) {
		this.keyValueCount = keyValueCount;
	}
	
	
}
