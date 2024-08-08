package quadric.util;

import quadric.blockvaulter.GetResult;

public class PathedGetResult extends GetResult {
	public PathedGetResult(GetResult rez) {
		this.in = rez.in;
		this.len = rez.len;
		this.md5 = rez.md5;
	}

	public String localPath;

}
