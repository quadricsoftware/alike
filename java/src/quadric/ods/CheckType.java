package quadric.ods;

public enum CheckType {
	none(0),
	quadradic(1),
	full(2);
	
	private final int value;
	
	CheckType(final int v) {
		value = v;
	}
	
	public int getValue() { return value; }
}
