package quadric.vhd;

import quadric.util.AutoStruct;

public class VhdParent extends AutoStruct {
	public static final String PLAT_CODE_MACX = "MacX";
	public static final String PLAT_CODE_W2KU = "W2ku";
	public static final String PLAT_CODE_W2RU = "W2ru";
	
	private byte [] locator_4 = new byte[4];
	private int platformSectorSize = 512;
	private int platformDataSize;
	private byte [] reserved_4 = new byte[4];
	private long platformOffset;
	
	
	public VhdParent() {
		super.setBigEndian();
	}
	
	public byte [] get01Locator_4() {
		return locator_4;
	}
	
	public void set01Locator_4(byte [] locator_4) {
		this.locator_4 = locator_4;
	}
	
	public int get02PlatformSectorSize() {
		return platformSectorSize;
	}
	
	public void set02PlatformSectorSize(int platformSectorSize) {
		this.platformSectorSize = platformSectorSize;
	}
	
	public int get03PlatformDataSize() {
		return platformDataSize;
	}
	
	public void set03PlatformDataSize(int platformDataSize) {
		this.platformDataSize = platformDataSize;
	}
	
	public byte [] get04Reserved_4() {
		return reserved_4;
	}
	
	public void set04Reserved_4(byte [] reserved_4) {
		this.reserved_4 = reserved_4;
	}
	
	public long get05PlatformOffset() {
		return platformOffset;
	}
	
	public void set05PlatformOffset(long platformOffset) {
		this.platformOffset = platformOffset;
	}
	

}
