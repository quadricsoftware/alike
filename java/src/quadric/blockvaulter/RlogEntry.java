package quadric.blockvaulter;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;

public class RlogEntry {
	public static final int ENTRY_SIZE=57;
	public String fingerprint;
	
	public RlogEntry(byte [] bites) {
		init(bites);
	}
	
	public RlogEntry(InputStream is) throws IOException {
		byte[] bites = new byte[ENTRY_SIZE];
		int sent = 0;
		while(sent < ENTRY_SIZE) {
			int amt = is.read(bites, sent, (ENTRY_SIZE - sent));
			if(amt == -1) {
				throw new EOFException();
			}
			sent += amt;
		}
		init(bites);
	}
	
	private void init(byte[] bites) {
		if(bites.length != ENTRY_SIZE) {
			throw new IllegalArgumentException("Byte count wrong");
		}
		fingerprint = new String(Arrays.copyOfRange(bites, 0, 32), Charset.forName("US-ASCII"));
	}
}
