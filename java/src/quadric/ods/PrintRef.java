package quadric.ods;

import quadric.util.Print;

public class PrintRef extends Print {
	private long refCount = 0;

	public PrintRef() {
		super();
	}
	
	public PrintRef(String s) {
		super(s);
	}
	public PrintRef(Print p) {
		super(p.bytes);
	}
	
	public long getRefCount() {
		return refCount;
	}

	public void setRefCount(long refCount) {
		this.refCount = refCount;
	}
	
	
}
