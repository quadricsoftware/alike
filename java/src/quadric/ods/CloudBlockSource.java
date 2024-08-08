package quadric.ods;

import java.io.File;
import java.util.function.DoubleConsumer;

import quadric.blockvaulter.BandwidthCloudAdapter;
import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.GetResult;
import quadric.util.HclReaderUtil;
import quadric.util.HclWriterUtil;
import quadric.util.JobControl;
import quadric.util.Print;
import quadric.util.VaultUtil;

public class CloudBlockSource implements BlockSource {
	private CloudAdapter adp;
	private int count = 0;
	public CloudBlockSource(CloudAdapter adp) {
		this.adp = adp;
	}
	
	@Override
	public byte[] getBlock(Print p) {
		GetResult rez = adp.getBlock(p.toString(), 0);
		byte [] bites = new byte[(int) rez.len];
		VaultUtil.toBytes(rez, bites);
		return bites;
	}

	@Override
	public void load(HclWriterUtil needed, DoubleConsumer progress, JobControl c) {
		HclReaderUtil reader = new HclReaderUtil(needed.getPath());
		count = reader.createCursor().count();
		progress.accept(100);
		
		
	}
	
	public int count() {
		return count;
		
	}

	@Override
	public String getBlockPath(Print p) {
		String foo = adp.getBlockPath(p);
		if(foo != null) {
			if(adp instanceof BandwidthCloudAdapter) {
				BandwidthCloudAdapter bca = (BandwidthCloudAdapter) adp;
				bca.getMeter().getDownloadMeter().meter(new File(foo).length());
			}
		}
		return foo;
	}
	
	
}
