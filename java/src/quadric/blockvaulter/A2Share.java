package quadric.blockvaulter;

/**
 * Represents a share entry in the A2 shares database
 *
 */
public class A2Share {
	private String localPath;
	
	
	
	public String getLocalPath() {
		return localPath;
	}
	public void setLocalPath(String localPath) {
		this.localPath = localPath;
	}
	
	@Override
	public String toString() {
		return "A2Share [localPath=" + localPath  + "]";
	}
	
	
}
