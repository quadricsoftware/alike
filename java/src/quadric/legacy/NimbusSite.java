package quadric.legacy;

public class NimbusSite {

	private int siteId;
	private long vmid;
	private long version;
	
	public NimbusSite() { ;
	}
	
	public NimbusSite(int siteId, long vmid, long version) {
		super();
		this.siteId = siteId;
		this.vmid = vmid;
		this.version = version;
	}

	public int getSiteId() {
		return siteId;
	}

	public void setSiteId(int siteId) {
		this.siteId = siteId;
	}

	public long getVmId() {
		return vmid;
	}

	public void setVmId(long vmid) {
		this.vmid = vmid;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	@Override
	public String toString() {
		return "NimbusSite [siteId=" + siteId + ", vmid=" + vmid + ", version=" + version + "]";
	}
	
	
};
