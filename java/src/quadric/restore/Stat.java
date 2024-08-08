package quadric.restore;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import quadric.util.ByteStruct;

public class Stat implements ByteStruct<Stat> {
	public long size;
	public long type;
	public long date;
	
	
	@Override
	public int compareTo(Stat arg0) {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public void load(byte[] bites) {
		ByteBuffer buffy = ByteBuffer.wrap(bites);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		size = buffy.getLong();
		type = buffy.getLong();
		date = buffy.getLong();
	}
	
	@Override
	public byte[] store() {
		ByteBuffer buffy = ByteBuffer.allocate(recordSize());
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		buffy.putLong(size);
		buffy.putLong(type);
		buffy.putLong(date);
		return buffy.array();
	}
	
	@Override
	public int recordSize() {
		return 24;
	}

}
