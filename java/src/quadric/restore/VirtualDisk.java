package quadric.restore;

public abstract class VirtualDisk {
	protected long imgSize;
	
	public VirtualDisk(long imgSize) {
		this.imgSize = imgSize;
	}
	
	
	public long getDiskSize() {
		return imgSize;
	}
	
	public abstract long getFileSize();
	
	public abstract int read(byte [] data, long offset, int start, int amt);
}
