package quadric.gfs;

import quadric.ods.VmVersion;

public class GfsVersion {
	private String installId;
	private String uuuid;
	private int scheduleId;
	private int siteId;
	private long epoch;
	
	public GfsVersion() { ; }
	
	public GfsVersion(VmVersion v, String installId, int scheduleId) {
		this.installId = installId;
		this.uuuid = v.getPlatformStyleUuid();
		this.scheduleId = scheduleId;
		this.siteId = v.getSiteId();
		this.epoch = v.getVersion();
	}
	
	public String getInstallId() {
		return installId;
	}
	public void setInstallId(String installId) {
		this.installId = installId;
	}
	public String getUuuid() {
		return uuuid;
	}
	public void setUuuid(String uuuid) {
		this.uuuid = uuuid;
	}
	public int getScheduleId() {
		return scheduleId;
	}
	public void setScheduleId(int scheduleId) {
		this.scheduleId = scheduleId;
	}
	public int getSiteId() {
		return siteId;
	}
	public void setSiteId(int siteId) {
		this.siteId = siteId;
	}
	public long getEpoch() {
		return epoch;
	}
	public void setEpoch(long epoch) {
		this.epoch = epoch;
	}
}
