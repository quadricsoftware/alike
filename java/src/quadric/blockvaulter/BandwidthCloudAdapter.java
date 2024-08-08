package quadric.blockvaulter;

import java.io.IOException;
import java.io.InputStream;

import quadric.stats.StatsManager;
import quadric.util.BandwidthMeter;
import quadric.util.MeteredOutputStream;
import quadric.util.Print;
import quadric.util.MeteredInputStream;

public class BandwidthCloudAdapter extends CloudAdapter {
	private static final int METADATA_OP_SIZE = 128;
	private static final int METER_ONE_OFF_THRESH = 1024 * 1024;
	
	private CloudAdapter adp;
	private BandwidthMeter meter;
	private String name;
	
	public BandwidthCloudAdapter(CloudAdapter adp, BandwidthMeter meter, String name, int siteId) {
		super(siteId);
		this.name = name;
		this.adp = adp;
		this.meter = meter;
		//this.readOnly = adp.readOnly;
		StatsManager.instance().register(name + "_down.bandwidth", () -> Math.round(meter.getDownloadMeter().getRate()) );
		StatsManager.instance().register(name + "_up.bandwidth", () -> Math.round(meter.getUploadMeter().getRate()) );
	}
	
	public BandwidthMeter getMeter() {
		return meter;
	}
	
	
	@Override
	public boolean putBlock(String path, InputStream in, long len, String dr5) {
		if(isReadOnly()) {
			throw new CloudException("Read-only mode is active");
		}
		if(len < METER_ONE_OFF_THRESH) {
			meter.getUploadMeter().meter(len);
			return adp.putBlock(path, in, len, dr5);
		} else {
			try (MeteredInputStream bis = new MeteredInputStream(in, meter, true)) {
				return adp.putBlock(path, bis, len, dr5);
			} catch(IOException ioe) {
				throw new CloudException(ioe);
			}
		}
	}

	@Override
	public GetResult getBlock(String path, long max) {
		GetResult rez = adp.getBlock(path, max);
		MeteredInputStream mis = new MeteredInputStream(rez.in, meter);
		rez.in = mis;
		return rez;
				
	}

	@Override
	public boolean stat(String path) {
		meter.getDownloadMeter().meter(METADATA_OP_SIZE);
		return adp.stat(path);
	}

	@Override
	public void del(String path) {
		if(isReadOnly()) {
			throw new CloudException("Read-only mode is active");
		}
		meter.getDownloadMeter().meter(METADATA_OP_SIZE);
		adp.del(path);
	}

	@Override
	public String id(String path) {
		meter.getDownloadMeter().meter(METADATA_OP_SIZE);
		return adp.id(path);
	}
	
	@Override
	public boolean cantVerifyKurganBlocks() {
		return adp.cantVerifyKurganBlocks();
	}
	
	@Override 
	public String getBlockPath(Print p) {
		return adp.getBlockPath(p);
		
	}

	@Override
	public DataStoreType getType() {
		return adp.getType();
	}
	
	public CloudAdapter getWrapped() {
		return adp;
	}
	
	@Override 
	protected void finalize() {
		StatsManager.instance().unregister(this.name + "_up");
		StatsManager.instance().unregister(this.name + "_down");
	}

}
