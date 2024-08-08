package quadric.spdb;

import quadric.blockvaulter.CloudException;

public class SpdbException extends CloudException {
	public SpdbException(Exception e) {
		super(e);
	}
	
	public SpdbException(String s) {
		super(s);
	}
}
