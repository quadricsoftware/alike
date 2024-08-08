package quadric.ods;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import quadric.blockvaulter.A2Share;
import quadric.blockvaulter.CloudException;
import quadric.crypt.CryptUtil;

public class DsInfo {
	private int siteId;
	private String lastTransactMd5;
	private long timestamp;
	private String identifier;
	private MaintStats maintStats = new MaintStats();
	
	
	
	public DsInfo() {
		lastTransactMd5 = "";
	}
	
	public DsInfo(int siteId) {
		this.siteId = siteId;
	}
	
	public int getSiteId() {
		return siteId;
	}
	public void setSiteId(int siteId) {
		this.siteId = siteId;
	}
	public String getLastTransactMd5() {
		return lastTransactMd5;
	}
	public void setLastTransactMd5(String lastTransactMd5) {
		this.lastTransactMd5 = lastTransactMd5;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Represents the unique id of this offiste location, produced by hashing up its ds settings
	 * @return
	 */
	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}
	
	/**
	 * Hashes up a lot of stuff from settings to create your identifier
	 * @param siteId
	 * @param sets
	 */
	public void setIdFromShare(A2Share share) {
		String combo = share.getLocalPath();
		try {
			identifier = CryptUtil.makeMd5Hash(combo.getBytes("US-ASCII"));
		} catch (UnsupportedEncodingException e) {
			throw new CloudException(e);
		}
	}

	
	public MaintStats getMaintStats() {
		return maintStats;
	}
	
	public void setMaintStats(MaintStats ms) {
		this.maintStats = ms;
	}

	
}
