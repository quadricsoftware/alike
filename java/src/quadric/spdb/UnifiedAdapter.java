package quadric.spdb;

import java.io.File;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.DataStoreType;
import quadric.crypt.CryptUtil;

public class UnifiedAdapter extends FsAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( UnifiedAdapter.class.getName() );
	private static final char digits[] = { '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
	public static final int HEX_DEPTH = 3;
	
	public UnifiedAdapter(int siteId, boolean useLocalTempForPuts) {
		super(siteId, false, useLocalTempForPuts);
	}

	
	@Override
	public void sanityCheck() {
		File test = new File(base + "/journals");
		if(test.exists() == false) {
			throw new CloudException("Datastore not properly mounted--file " + test.getAbsolutePath() + " does not exist!");
		}
	}
	
	@Override
	public void prepop(String blockPath) {
		
		File bloxGuy = new File(bloxPath);
		if(bloxGuy.exists() == false) {
			bloxGuy.mkdir();
		}
		File lastGuy = new File(createPath(15, 15, 15));
		if(lastGuy.exists()) {
			return;
		}
		LOGGER.info("Directory " + lastGuy + " does not exist, will generate blocks hierarchy. Jobs will fail until completed.");
		for(int x = 0; x < 16; ++x) {
			for(int y = 0; y < 16; ++y) {
				for(int z = 0; z < 16; ++z) {
					String pathname = createPath(x, y, z);
					new File(pathname).mkdir();
				}
			}
		}
		
	}

	@Override
	public String getBlockPath(String name) {
		if(super.prepopComplete == false) {
			throw new CloudNotReadyException("Block directory generation not complete yet"); 
		}
		name = name.toLowerCase();
		return bloxPath + File.separator + name.substring(0, HEX_DEPTH) + File.separator + name;
	}

	@Override
	public DataStoreType getType() {
		return DataStoreType.unified;
	}
	
	private String createPath(int a, int b, int c) {
		return bloxPath + File.separator + digits[a] + digits[b] + digits[c];
	}

	
}
