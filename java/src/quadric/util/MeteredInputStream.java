package quadric.util;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MeteredInputStream extends InputStream {
	InputStream is;
	BandwidthMeter band;
	boolean reversePolarity = false;
	public MeteredInputStream(InputStream is, BandwidthMeter b) {
		super();
		this.band = b;
		this.is = is;
	}
	
	public MeteredInputStream(InputStream is, BandwidthMeter b, boolean treatAsUpload) {
		super();
		this.band = b;
		this.is = is;
		this.reversePolarity = treatAsUpload;
	}

	@Override
	public int read() throws IOException {
		if(reversePolarity == false) {
			band.getDownloadMeter().meter(1);
		} else {
			band.getUploadMeter().meter(1);
		}
		return is.read();
	}
	
	@Override
	public int read(byte [] b, int off, int len) throws IOException {
		if(reversePolarity == false) {
			band.getDownloadMeter().meter(len);
		} else {
			band.getUploadMeter().meter(len);
		}
		return is.read(b, off, len);
	}
	
	@Override
	public int read(byte [] b) throws IOException {
		return read(b, 0, b.length);
	}
	
	@Override
	public void close() throws IOException {
		if(is != null) {
			is.close();
		}
		super.close();
	}

}
