package quadric.vhdx;

import quadric.util.AutoStruct;

public class VhdxHeader extends AutoStruct {
	public static final int SIG = 0x64616568;
	
	private int signature = SIG;
	private int checksum;
	private long sequenceNumber;
	private Guid fileWriteGuid = new Guid();
	private Guid dataWriteGuid = new Guid();
	private Guid logGuid = new Guid();
	private short logVersion;
	private short version;
	private int logLength;
	private long logOffset;
	private byte [] reserved_4016 = new byte[4016];
	
	public VhdxHeader() { ; }
	
	public VhdxHeader(VhdxHeader orig) {
		byte [] cruft = orig.store();
		this.load(cruft);
	}

	public int get01Signature() {
		return signature;
	}

	public void set01Signature(int signature) {
		this.signature = signature;
	}

	public int get02Checksum() {
		return checksum;
	}

	public void set02Checksum(int checksum) {
		this.checksum = checksum;
	}

	public long get03SequenceNumber() {
		return sequenceNumber;
	}

	public void set03SequenceNumber(long sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public Guid get04FileWriteGuid() {
		return fileWriteGuid;
	}

	public void set04FileWriteGuid(Guid fileWriteGuid) {
		this.fileWriteGuid = fileWriteGuid;
	}

	public Guid get05DataWriteGuid() {
		return dataWriteGuid;
	}

	public void set05DataWriteGuid(Guid dataWriteGuid) {
		this.dataWriteGuid = dataWriteGuid;
	}

	public Guid get06LogGuid() {
		return logGuid;
	}

	public void set06LogGuid(Guid logGuid) {
		this.logGuid = logGuid;
	}

	public short get07LogVersion() {
		return logVersion;
	}

	public void set07LogVersion(short logVersion) {
		this.logVersion = logVersion;
	}

	public short get08Version() {
		return version;
	}

	public void set08Version(short version) {
		this.version = version;
	}

	public int get09LogLength() {
		return logLength;
	}

	public void set09LogLength(int logLength) {
		this.logLength = logLength;
	}

	public long get10LogOffset() {
		return logOffset;
	}

	public void set10LogOffset(long logOffset) {
		this.logOffset = logOffset;
	}

	public byte[] get11Reserved_4016() {
		return reserved_4016;
	}

	public void set11Reserved_4016(byte[] reserved_4016) {
		this.reserved_4016 = reserved_4016;
	}
	
	
	
}
