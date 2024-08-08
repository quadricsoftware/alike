package quadric.util;

import java.util.Iterator;

public class IteratorCursor implements HclCursor {
	private int count;
	private int pos = 0;
	private Iterator<Print> i;

	public IteratorCursor(Iterator<Print> i, int count) {
		this.count = count;
		this.i = i;
	}
	
	@Override
	public boolean hasNext() {
		return i.hasNext();
	}

	@Override
	public Print next() {
		pos++;
		return i.next();
	}

	@Override
	public int getPosition() {
		return pos;
	}

	@Override
	public int count() {
		return count;
	}
	
	public byte [] bulk(int count) {
		return null;
	}

}
