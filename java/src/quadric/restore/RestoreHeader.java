package quadric.restore;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import quadric.util.ByteStruct;

public class RestoreHeader implements ByteStruct<RestoreHeader> {
	public long clientId;
	public long offset;
	public long pipeId;
	public int pathLen;
	public int length;
	public int command;
	public int reserved2;
	
	@Override
	public int compareTo(RestoreHeader r) {
		return 0;
	}

	@Override
	public void load(byte[] bites) {
		ByteBuffer buffy = ByteBuffer.wrap(bites);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		clientId = buffy.getLong();
		offset = buffy.getLong();
		pipeId = buffy.getLong();
		pathLen = buffy.getInt();
		length = buffy.getInt();
		command = buffy.getInt();
		reserved2 = buffy.getInt();
		
	}

	@Override
	public byte[] store() {
		ByteBuffer buffy = ByteBuffer.allocate(recordSize());
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		buffy.putLong(clientId);
		buffy.putLong(offset);
		buffy.putLong(pipeId);
		buffy.putInt(pathLen);
		buffy.putInt(length);
		buffy.putInt(command);
		buffy.putInt(reserved2);
		return buffy.array();
	}

	@Override
	public int recordSize() {
		return 40;
	}
	
	@Override
	public String toString() {
		return "RestoreHeader client: " + clientId  + " command: " + command + " pipeId: " + pipeId + " pathLen: " + pathLen + " offset: " + offset + " length: " + length;
	}
	
	public void truncateReadIfNeeded(long fileSize) {
		int newLength = length;
		if(length + offset > fileSize) {
			newLength = (int) (fileSize - offset);
			// WTF guys? Are you trying to start out-of-bounds?
			if(newLength >= length) {
				length = 0;
			} else {
				length = newLength;
			}
		}
	}
	

}
