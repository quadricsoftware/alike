package quadric.ods;

public class MaintStats {
	private long purgeTime;
	private long lastPurge;
	private long reconTime;
	private long lastRecon;
	private int journalCount;
	private int reconnedCount;
	
	
	
	public MaintStats() {
	
	}
	
	public long getPurgeTime() {
		return purgeTime;
	}
	public void setPurgeTime(long purgeTime) {
		this.purgeTime = purgeTime;
	}
	public long getLastPurge() {
		return lastPurge;
	}
	public void setLastPurge(long lastPurge) {
		this.lastPurge = lastPurge;
	}

	public long getReconTime() {
		return reconTime;
	}

	public void setReconTime(long reconTime) {
		this.reconTime = reconTime;
	}

	public long getLastRecon() {
		return lastRecon;
	}

	public void setLastRecon(long lastRecon) {
		this.lastRecon = lastRecon;
	}

	public int getJournalCount() {
		return journalCount;
	}

	public void setJournalCount(int journalCount) {
		this.journalCount = journalCount;
	}

	public int getReconnedCount() {
		return this.journalCount - reconnedCount;
	}

	public void setUnReconnedCount(int reconnedCount) {
		this.reconnedCount = reconnedCount;
	}
	
	

}
