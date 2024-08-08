package quadric.vhdx;

import quadric.util.AutoStruct;

public class VhdxLogDataSector extends AutoStruct {
	public static final int SIG = 0x61746164;
	
	int dataSignature;
	int sequenceHigh;
	byte [] data_4084 = new byte[4084];
	int sequenceLow;
	
	public int get1DataSignature() {
		return dataSignature;
	}
	public void set1DataSignature(int dataSignature) {
		this.dataSignature = dataSignature;
	}
	public int get2SequenceHigh() {
		return sequenceHigh;
	}
	public void set2SequenceHigh(int sequenceHigh) {
		this.sequenceHigh = sequenceHigh;
	}
	public byte[] get3Data_4084() {
		return data_4084;
	}
	public void set3Data_4084(byte[] data_4084) {
		this.data_4084 = data_4084;
	}
	public int get4SequenceLow() {
		return sequenceLow;
	}
	public void set4SequenceLow(int sequenceLow) {
		this.sequenceLow = sequenceLow;
	}
	
}
