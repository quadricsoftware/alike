package quadric.util;

import java.io.UnsupportedEncodingException;

import quadric.blockvaulter.CloudException;
import quadric.crypt.CryptUtil;

public class OffsetEntry implements ByteStruct<OffsetEntry> {
	public static final int RECORD_SIZE = 32 + 16 + 2;
	public String print;
	public int offset;
	
	@Override
	public int compareTo(OffsetEntry o) {
		return this.print.compareToIgnoreCase(o.print);
	}

	@Override
	public void load(byte[] bites) {
		String tmp;
		try {
			 tmp = new String(bites, "US-ASCII");
		} catch (UnsupportedEncodingException e) {
			throw new CloudException(e);
		}
		print = tmp.substring(0, 32);
		offset = (int) CryptUtil.hexToLong(tmp.substring(32));
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(print);
		sb.append(CryptUtil.longToHex(offset));
		if(sb.length() != RECORD_SIZE) {
			throw new CloudException("OffsetEntry is invalid");
		}
		return sb.toString();
	}
	
	@Override
	public byte[] store() {
		
		try {
			return toString().getBytes("US-ASCII");
		} catch (UnsupportedEncodingException e) {
			throw new CloudException(e);
		}
	}

	@Override
	public int recordSize() {
		return RECORD_SIZE;
	}
	
}