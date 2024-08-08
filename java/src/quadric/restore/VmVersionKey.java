package quadric.restore;

import quadric.ods.VmVersion;

public class VmVersionKey {
	private long version;
	private String uuid;
	private int siteId;
	
	public VmVersionKey() {
		;
	}
	
	public VmVersionKey(VmVersion crap) {
		this.version = crap.getVersion();
		this.uuid = crap.getNormalizedUuid();
		this.siteId = crap.getSiteId();
	}
	public int getSiteId() {
		return siteId;
	}
	public void setSiteId(int siteId) {
		this.siteId = siteId;
	}
	public long getVersion() {
		return version;
	}
	public void setVersion(long version) {
		this.version = version;
	}
	public String getUuid() {
		return uuid;
	}
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + siteId;
		result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
		result = prime * result + (int) (version ^ (version >>> 32));
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
		VmVersionKey other = (VmVersionKey) obj;
		if (siteId != other.siteId)
			return false;
		if (uuid == null) {
			if (other.uuid != null)
				return false;
		} else if (!uuid.equals(other.uuid))
			return false;
		if (version != other.version)
			return false;
		return true;
	}
	
	
}
