package quadric.vhd;

import quadric.blockvaulter.CloudException;
import quadric.util.AutoStruct;

public class VhdFooter extends AutoStruct {
	private byte [] cookie_8;
	private int features = 	0x00000002;
	private int version = 	0x00010000;
	private long dataOffset;
	private int timeStamp;
	private byte [] creatorApp_4;
	private int creatorVersion;
	private int createorOs;
	private long origSize;
	private long currentSize;
	private int diskGeom;
	private int diskType;
	private int checksum;
	private byte [] uniqueId_16 = new byte[16];
	private byte savedState;
	private byte [] reserved_427 = new byte[427];
	
	
	
	public VhdFooter() {
		super.setBigEndian();
		try {
			cookie_8 = "conectix".getBytes("US-ASCII");
			creatorApp_4 = "QSA2".getBytes("US-ASCII");
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



	public int get02Features() {
		return features;
	}



	public void set02Features(int features) {
		this.features = features;
	}



	public int get03Version() {
		return version;
	}



	public void set03Version(int version) {
		this.version = version;
	}



	public long get04DataOffset() {
		return dataOffset;
	}



	public void set04DataOffset(long dataOffset) {
		this.dataOffset = dataOffset;
	}



	public int get05TimeStamp() {
		return timeStamp;
	}



	public void set05TimeStamp(int timeStamp) {
		this.timeStamp = (timeStamp - VirtualVhd.VHD_EPOCH_BEGIN);
	}



	public byte[] get06CreatorApp_4() {
		return creatorApp_4;
	}



	public void set06CreatorApp_4(byte[] creatorApp_4) {
		this.creatorApp_4 = creatorApp_4;
	}



	public int get07CreatorVersion() {
		return creatorVersion;
	}



	public void set07CreatorVersion(int creatorVersion) {
		this.creatorVersion = creatorVersion;
	}



	public int get08CreateorOs() {
		return createorOs;
	}



	public void set08CreateorOs(int createorOs) {
		this.createorOs = createorOs;
	}



	public long get09OrigSize() {
		return origSize;
	}



	public void set09OrigSize(long origSize) {
		this.origSize = origSize;
	}



	public long get10CurrentSize() {
		return currentSize;
	}



	public void set10CurrentSize(long currentSize) {
		this.currentSize = currentSize;
	}



	public int get11DiskGeom() {
		return diskGeom;
	}



	public void set11DiskGeom(int diskGeom) {
		this.diskGeom = diskGeom;
	}



	public int get12DiskType() {
		return diskType;
	}



	public void set12DiskType(int diskType) {
		this.diskType = diskType;
	}



	public int get13Checksum() {
		return checksum;
	}



	public void set13Checksum(int checksum) {
		this.checksum = checksum;
	}



	public byte[] get14UniqueId_16() {
		return uniqueId_16;
	}



	public void set14UniqueId_16(byte[] uniqueId_16) {
		this.uniqueId_16 = uniqueId_16;
	}



	public byte get15SavedState() {
		return savedState;
	}



	public void set15SavedState(byte savedState) {
		this.savedState = savedState;
	}



	public byte[] get16Reserved_427() {
		return reserved_427;
	}



	public void set16Reserved_427(byte[] reserved_427) {
		this.reserved_427 = reserved_427;
	}
	
	

}
