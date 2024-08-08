package quadric.blockvaulter;

public enum DataStoreType {
	cifs(0), 
	S3(1),
	speedy(4),
	local(3),
	unified(2);
	
	private final int value;
	
	DataStoreType(final int v) {
		value = v;
	}
	
	public int getValue() { return value; }
}
	
