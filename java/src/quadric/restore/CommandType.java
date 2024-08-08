package quadric.restore;

public enum CommandType {
	data(0), 
	attr(1),
	list(2),
	followLink(3),
	openAmb(4),
	closeAmb(5),	// No longer used
	writeAmb(6);
	
	private final int value;
	
	CommandType(final int v) {
		value = v;
	}
	
	public int getValue() { return value; }
}
