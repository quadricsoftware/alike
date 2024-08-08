package quadric.ods;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import quadric.util.ByteStruct;

public class EclDisk implements ByteStruct<EclDisk> {
	private long diskSize;
	private int hclCount;
	
	@Override
	public void load(byte[] bites) {
		ByteBuffer buffy = ByteBuffer.wrap(bites);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		diskSize = buffy.getLong();
		hclCount = buffy.getInt();
		
	}

	@Override
	public byte[] store() {
		ByteBuffer buffy = ByteBuffer.allocate(recordSize());
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		buffy.putLong(diskSize);
		buffy.putInt((int) hclCount);
		return buffy.array();
	}

	@Override
	public int recordSize() {
		return 12;
	}

	@Override
	public int compareTo(EclDisk o) {
		return 0;
	}

	public long getDiskSize() {
		return diskSize;
	}

	public void setDiskSize(long diskSize) {
		this.diskSize = diskSize;
	}

	public int getHclCount() {
		return hclCount;
	}

	public void setHclCount(int hclCount) {
		this.hclCount = hclCount;
	}

}
