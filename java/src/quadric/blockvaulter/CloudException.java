package quadric.blockvaulter;

public class CloudException extends RuntimeException {
	private int errorCode = 0;
	public CloudException(String msg) {
		super(msg);
	}
	
	public CloudException(String msg, int code) {
		super(msg);
		this.errorCode = code;
	}
	
	public CloudException(Throwable e) {
		super(e);
	}
	
	public CloudException(String f, Throwable e) {
		super(f,e);
	}
	
	public CloudException(Throwable e, int c) {
		super(e);
		this.errorCode = c;
	}
	
	public int getErrorCode() {
		return errorCode;
	}
	
	public void setErrorCode(int c) {
		this.errorCode = c;
	}
	
}