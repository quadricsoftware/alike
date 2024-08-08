package quadric.util;

import java.util.Iterator;

public interface HclCursor extends Iterator<Print> {
	public boolean hasNext();
	public Print next();
	public byte [] bulk(int count);
	public int getPosition();
	public int count();
}
