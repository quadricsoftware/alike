package quadric.vhdx;

import quadric.util.AutoStruct;

public class VhdxLogEntryHeader extends AutoStruct {
	public static final int SIG = 0x65676F6C;
	
	private int signature = SIG;
	private int checksum;
	private int entryLength;
	private int tail;
	private long sequenceNumber;
	private int descriptorCount;
	private int reserved;
	private Guid logGuid = new Guid();
	private long flushedFileOffset;
	private long lastFileOffset;
	
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
	public int get03EntryLength() {
		return entryLength;
	}
	public void set03EntryLength(int entryLength) {
		this.entryLength = entryLength;
	}
	public int get04Tail() {
		return tail;
	}
	public void set04Tail(int tail) {
		this.tail = tail;
	}
	public long get05SequenceNumber() {
		return sequenceNumber;
	}
	public void set05SequenceNumber(long sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}
	public int get06DescriptorCount() {
		return descriptorCount;
	}
	public void set06DescriptorCount(int descriptorCount) {
		this.descriptorCount = descriptorCount;
	}
	public int get07Reserved() {
		return reserved;
	}
	public void set07Reserved(int reserved) {
		this.reserved = reserved;
	}
	public Guid get08LogGuid() {
		return logGuid;
	}
	public void set08LogGuid(Guid logGuid) {
		this.logGuid = logGuid;
	}
	public long get09FlushedFileOffset() {
		return flushedFileOffset;
	}
	public void set09FlushedFileOffset(long flushedFileOffset) {
		this.flushedFileOffset = flushedFileOffset;
	}
	public long get10LastFileOffset() {
		return lastFileOffset;
	}
	public void set10LastFileOffset(long lastFileOffset) {
		this.lastFileOffset = lastFileOffset;
	}
}
