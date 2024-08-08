package quadric.gfs;

public class SchedKey {
	public SchedKey(SchedKey inKey) {
		this.sid = inKey.sid;
		this.installId = inKey.installId;
		this.siteId = inKey.siteId;
	}
	
	public SchedKey() {
		;
	}

	public int sid;
	public String installId;
	public int siteId;
}
