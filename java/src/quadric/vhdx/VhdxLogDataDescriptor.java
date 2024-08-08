package quadric.vhdx;

public class VhdxLogDataDescriptor {
	public static final int SIG = 0x63736564;
	
	int dataSignature = SIG;
	int trailingBytes;
	long leadingBytes;
	long fileOffset;
	long sequenceNumber;
	
	public int get1DataSignature() {
		return dataSignature;
	}
	public void set1DataSignature(int dataSignature) {
		this.dataSignature = dataSignature;
	}
	public int get2TrailingBytes() {
		return trailingBytes;
	}
	public void set2TrailingBytes(int trailingBytes) {
		this.trailingBytes = trailingBytes;
	}
	public long get3LeadingBytes() {
		return leadingBytes;
	}
	public void set3LeadingBytes(long leadingBytes) {
		this.leadingBytes = leadingBytes;
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
