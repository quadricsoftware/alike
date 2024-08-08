package quadric.util;

import java.io.ByteArrayInputStream;

public class SeeThruByteArrayInputStream extends ByteArrayInputStream {

	public SeeThruByteArrayInputStream(byte[] arg0) {
		super(arg0);
	}
	
	public byte [] reveal() {
		return buf;
	}

}
