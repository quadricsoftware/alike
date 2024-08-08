package quadric.spdb;

import quadric.blockvaulter.CloudException;

public class CloudNotReadyException extends CloudException {

	public CloudNotReadyException(String msg) {
		super(msg);
	}

	public CloudNotReadyException(String msg, int code) {
		super(msg, code);
	}

	public CloudNotReadyException(Throwable e) {
		super(e);
	}

	public CloudNotReadyException(String f, Throwable e) {
		super(f, e);
	}

	public CloudNotReadyException(Throwable e, int c) {
		super(e, c);
	}

}
