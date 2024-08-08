package quadric.restore;

public class EclCacheKey {
	public EclCacheKey() {
		;
	}
	
	public EclCacheKey(int siteId, long flagId, int diskNum) {
		this.siteId = siteId;
		this.flagId = flagId;
		this.diskNum = diskNum;
	}
	
	private int siteId;
	private long flagId;
	private int diskNum;
	
	
	public String getEclPath() {
		String adsOds = "ads";
		if(this.getSiteId() != 0) {
			adsOds = "ods" + this.getSiteId();
		}
		return "/mnt/" + adsOds + "/journals/" + this.getFlagId() + ".ecl"; 
	}
	
	public int getSiteId() {
		return siteId;
	}
	public void setSiteId(int siteId) {
		this.siteId = siteId;
	}
	public long getFlagId() {
		return flagId;
	}
	public void setFlagId(long flagId) {
		this.flagId = flagId;
	}
	public int getDiskNum() {
		return diskNum;
	}
	public void setDiskNum(int diskNum) {
		this.diskNum = diskNum;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + diskNum;
		result = prime * result + (int) (flagId ^ (flagId >>> 32));
		result = prime * result + siteId;
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
		EclCacheKey other = (EclCacheKey) obj;
		if (diskNum != other.diskNum)
			return false;
		if (flagId != other.flagId)
			return false;
		if (siteId != other.siteId)
			return false;
		return true;
	}
}
