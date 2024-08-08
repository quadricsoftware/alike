package quadric.vhdx;

import quadric.util.AutoStruct;

public class VhdxFileIdentifier extends AutoStruct {
	public static final long SIG_VAL = 0x656C696678646876L;
	
	private long signature = SIG_VAL;
	private byte[] creator_512 = new byte[512];
	
	public long get1Signature() {
		return signature;
	}
	public void set1Signature(long signature) {
		this.signature = signature;
	}
	public byte[] get2Creator_512() {
		return creator_512;
	}
	public void set2Creator_512(byte [] creator) {
		this.creator_512 = creator;
	}
	
}
