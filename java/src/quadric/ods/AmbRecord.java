package quadric.ods;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import quadric.blockvaulter.CloudException;
import quadric.util.ByteStruct;
import quadric.util.Print;

/**
 * This is an internal AMB record....
 *
 */
public class AmbRecord implements ByteStruct<AmbRecord> {
	static final int MY_SIZE = AmbBlockSource.BLOCKDESC_STRUCT_SIZE + 8 + 2;
	static byte[] BLANK_BYTES = new byte[MY_SIZE];
	Print p;
	int sz;
	long offset;
	short ambFile;
	
	
	@Override
	public int compareTo(AmbRecord r2) {
		return this.p.compareTo(r2.p);
	}

	@Override
	public void load(byte[] bites) {
		if(Arrays.equals(bites, BLANK_BYTES) == true) {
			p = new Print(BLANK_BYTES);
			sz = -1;
			offset = -1;
			ambFile = -1;
			return;
		}
		
		try {
			String tmpS = new String(bites, 4, 32, "US-ASCII");
			p = new Print(tmpS);
		} catch (UnsupportedEncodingException e) {
			throw new CloudException(e);
		}
		ByteBuffer buffy = ByteBuffer.wrap(bites);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		sz = buffy.getInt();
		// Skip the printy--and the struct-aligned hot mess
		buffy.position(AmbBlockSource.BLOCKDESC_STRUCT_SIZE);
		offset = buffy.getLong();
		ambFile = buffy.getShort();
	}

	@Override
	public byte[] store() {
		throw new CloudException("Not implemented");
	}

	@Override
	public int recordSize() {
		return MY_SIZE;
	}
	
}
