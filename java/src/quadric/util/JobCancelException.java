package quadric.util;

import quadric.blockvaulter.CloudException;

public class JobCancelException extends CloudException {
	public JobCancelException() {
		super("Job canceled");
	}
}
