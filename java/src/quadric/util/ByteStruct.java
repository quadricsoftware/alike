package quadric.util;

public interface ByteStruct<T> extends Comparable<T> {
	public void load(byte [] bites);
	public byte [] store();
	public int recordSize();
}
