package quadric.vhd;

import quadric.blockvaulter.CloudException;
import quadric.util.AutoStruct;

public class VhdHeader extends AutoStruct {
	private byte [] cookie_8;
	private long dataOffset = 0xFFFFFFFF;
	private long tableOffset;
	private int headerVersion = 0x00010000;
	private int maxTableEntries;
	private int blockSize = (1024 * 1024 * 2);
	private int checksum;
	private byte [] parentId_16 = new byte[16];
	private int parentTimeStamp;
	private int reserved;
	private byte [] parentUnicodeName_512 = new byte[512];
	private byte [] parentLocatorEntries_192 = new byte[192];
	private byte [] reservedAgain_256 = new byte[256];
	
	
	
	public VhdHeader() {
		super.setBigEndian();
		try {
			cookie_8 = "cxsparse".getBytes("US-ASCII");
		} catch(Exception e) {
			throw new CloudException(e);
		}
	}



	public byte[] get01Cookie_8() {
		return cookie_8;
	}



	public void set01Cookie_8(byte[] cookie_8) {
		this.cookie_8 = cookie_8;
	}



	public long get02DataOffset() {
		return dataOffset;
	}



	public void set02DataOffset(long dataOffset) {
		this.dataOffset = dataOffset;
	}



	public long get03TableOffset() {
		return tableOffset;
	}



	public void set03TableOffset(long tableOffset) {
		this.tableOffset = tableOffset;
	}



	public int get04HeaderVersion() {
		return headerVersion;
	}



	public void set04HeaderVersion(int headerVersion) {
		this.headerVersion = headerVersion;
	}



	public int get05MaxTableEntries() {
		return maxTableEntries;
	}



	public void set05MaxTableEntries(int maxTableEntries) {
		this.maxTableEntries = maxTableEntries;
	}



	public int get06BlockSize() {
		return blockSize;
	}



	public void set06BlockSize(int blockSize) {
		this.blockSize = blockSize;
	}



	public int get07Checksum() {
		return checksum;
	}



	public void set07Checksum(int checksum) {
		this.checksum = checksum;
	}



	public byte[] get08ParentId_16() {
		return parentId_16;
	}



	public void set08ParentId_16(byte[] parentId_16) {
		this.parentId_16 = parentId_16;
	}



	public int get09ParentTimeStamp() {
		return parentTimeStamp;
	}



	public void set09ParentTimeStamp(int parentTimeStamp) {
		this.parentTimeStamp = (parentTimeStamp - VirtualVhd.VHD_EPOCH_BEGIN);
	}



	public int get10Reserved() {
		return reserved;
	}



	public void set10Reserved(int reserved) {
		this.reserved = reserved;
	}



	public byte[] get11ParentUnicodeName_512() {
		return parentUnicodeName_512;
	}



	public void set11ParentUnicodeName_512(byte[] parentUnicodeName_512) {
		this.parentUnicodeName_512 = parentUnicodeName_512;
	}


	public byte[] get12ParentLocatorEntries_192() {
		return parentLocatorEntries_192;
	}
	
	public void set12ParentLocatorEntries_192(byte [] parentLocatorEntries_192) {
		this.parentLocatorEntries_192 = parentLocatorEntries_192;
	}
	
	public byte[] get13ReservedAgain_256() {
		return reservedAgain_256;
	}



	public void set13ReservedAgain_256(byte[] reservedAgain_256) {
		this.reservedAgain_256 = reservedAgain_256;
	}
	
	

}
