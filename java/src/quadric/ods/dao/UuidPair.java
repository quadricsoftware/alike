package quadric.ods.dao;

public class UuidPair {
	private long id;
	private String platformUuid;
	
	public UuidPair() {
		;
	}
	
	public UuidPair(long id, String platformId) {
		this.id = id;
		this.platformUuid = platformId;
	}
	
	
	public String getPlatformUuid() {
		return platformUuid;
	}
	public void setPlatformUuid(String platformUuid) {
		this.platformUuid = platformUuid;
	}
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	
	
	
}
