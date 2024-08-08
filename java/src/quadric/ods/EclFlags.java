package quadric.ods;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;

import quadric.util.ByteStruct;

public class EclFlags implements ByteStruct<EclFlags> {
	public static final int MY_SIZE = 20;
	private int reconSeq = 0;
	private long ownerTs = 0;
	private long deleteTx = 0;
	// Not available in the flags file, but in its name instead
	private long txNo = 0;
	// Not available in flags file
	private int state = 0;
	// Not available in flags file
	private int siteId;
	

	@Override
	public void load(byte[] bites) {
		ByteBuffer buffy = ByteBuffer.wrap(bites);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		reconSeq = buffy.getInt();
		ownerTs = buffy.getLong();
		deleteTx = buffy.getLong();
		
	}

	@Override
	public byte[] store() {
		ByteBuffer buffy = ByteBuffer.allocate(MY_SIZE);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		buffy.putInt(reconSeq);
		buffy.putLong(ownerTs);
		buffy.putLong(deleteTx);
		return buffy.array();
	}

	public int getReconSeq() {
		return reconSeq;
	}

	public void setReconSeq(int reconSeq) {
		this.reconSeq = reconSeq;
	}

	public long getOwnerTs() {
		return ownerTs;
	}

	public void setOwnerTs(long ownerTs) {
		this.ownerTs = ownerTs;
	}

	public long getDeleteTx() {
		return deleteTx;
	}

	public void setDeleteTx(long deleteTx) {
		this.deleteTx = deleteTx;
	}

	public int getState() {
		return state;
	}

	public void setState(int state) {
		this.state = state;
	}
	
	public long getTxNo() {
		return txNo;
	}

	public void setTxNo(long txNo) {
		this.txNo = txNo;
	}

	public boolean flagsChangedNoState(EclFlags flags) {
		if(this.deleteTx != flags.deleteTx) return true;
		if(this.reconSeq != flags.reconSeq) return true;
		if(this.ownerTs != flags.ownerTs) return true;
		return false;
	}
	
	public boolean flagsChangedAny(EclFlags flags) {
		if(flagsChangedNoState(flags) == true) return true;
		if(this.state != flags.state) return true;
		return false;
	}

	@Override
	public int recordSize() {
		return MY_SIZE;
	}
	
	/* @Override
	public boolean equals(EclFlags e2) {
		if(e2.txNo != txNo) return false;
		if(e2.deleteTx != )
	}*/

	@Override
	public int compareTo(EclFlags o) {
		return new Long(txNo).compareTo(o.txNo);
	}

	public int getSiteId() {
		return siteId;
	}

	public void setSiteId(int siteId) {
		this.siteId = siteId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (deleteTx ^ (deleteTx >>> 32));
		result = prime * result + (int) (ownerTs ^ (ownerTs >>> 32));
		result = prime * result + reconSeq;
		result = prime * result + siteId;
		result = prime * result + state;
		result = prime * result + (int) (txNo ^ (txNo >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EclFlags other = (EclFlags) obj;
		if (deleteTx != other.deleteTx)
			return false;
		if (ownerTs != other.ownerTs)
			return false;
		if (reconSeq != other.reconSeq)
			return false;
		if (siteId != other.siteId)
			return false;
		if (state != other.state)
			return false;
		if (txNo != other.txNo)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		SimpleDateFormat fd = new SimpleDateFormat("MMM-dd-yyyy HH:mm:ss");
		return "FLAGS: Site: " + siteId 
				+ " txNo: " + txNo 
				+ " deleteTx: " + deleteTx 
				+ " state: " + state 
				+ " reconSeq: " + reconSeq 
				+ " ownerTs: " + ownerTs
				+ " (" + fd.format(ownerTs) + ")";
	}

	

}
