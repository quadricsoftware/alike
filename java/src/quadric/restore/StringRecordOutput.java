package quadric.restore;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * Writes a string to a stream in ASCII prepending the length of the string as a little-endian int32
 *
 */
public class StringRecordOutput {
	private OutputStream dest;
	
	public StringRecordOutput(OutputStream os) {
		this.dest = os;
	}
	
	public void sendRecord(String record) throws IOException {
		ByteBuffer buffy = ByteBuffer.allocate(Integer.BYTES);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		byte [] awesome = record.getBytes("US-ASCII");
		buffy.putInt(awesome.length);
		dest.write(buffy.array());
		if(awesome.length > 0) {
			dest.write(awesome);
		}
	}
	
	public void complete() throws IOException {
		sendRecord("");
	}
}
