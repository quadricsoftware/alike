package quadric.vhdx;

import quadric.util.AutoStruct;

public class VhdxLogZeroDescriptor extends AutoStruct {
	public static final int SIG = 0x6F72657A;
	
	private int zeroSignature= SIG;
	private int reserved;
	private long zeroLength;
	private long fileOffset;
	private long sequenceNumber;
	
	public int get1ZeroSignature() {
		return zeroSignature;
	}
	public void set1ZeroSignature(int zeroSignature) {
		this.zeroSignature = zeroSignature;
	}
	public int get2Reserved() {
		return reserved;
	}
	public void set2Reserved(int reserved) {
		this.reserved = reserved;
	}
	public long get3ZeroLength() {
		return zeroLength;
	}
	public void set3ZeroLength(long zeroLength) {
		this.zeroLength = zeroLength;
	}
	public long get4FileOffset() {
		return fileOffset;
	}
	public void set4FileOffset(long fileOffset) {
		this.fileOffset = fileOffset;
	}
	public long get5SequenceNumber() {
		return sequenceNumber;
	}
	public void set5SequenceNumber(long sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}
}
