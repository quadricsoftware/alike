package quadric.util;

import java.io.IOException;
import java.io.OutputStream;

public class MeteredOutputStream extends OutputStream {
	private OutputStream os;
	private BandwidthMeter band;
	
	public MeteredOutputStream(OutputStream os, BandwidthMeter band) {
		super();
		this.band = band;
		this.os = os;
		
	}
	
	@Override
	public void write(int b) throws IOException {
		band.getUploadMeter().meter(1);
		os.write(b);
	}
	
	@Override
	public void close() throws IOException {
		if(os != null) {
			os.close();
		}
		super.close();
	}

}
