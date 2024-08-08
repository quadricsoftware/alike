package quadric.legacy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.DataStoreType;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.VaultSettings;
import quadric.spdb.FsAdapter;
import quadric.spdb.SimpleAdapter;
import quadric.spdb.SpdbException;
import quadric.util.Pair;
import quadric.util.SeeThruByteArrayInputStream;

public class LegacyOdsAdapter extends FsAdapter {
	private static final char digits[] = { '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
	
	private Map<String,Boolean> buxExist = new HashMap<String,Boolean>();
	
	
	public LegacyOdsAdapter(int dsNumReal) {
		super(dsNumReal, true, false);
		
	}
	
	@Override
	public String getBlockPath(String print) {
		String sep = File.separator;
		String thePath = bloxPath + sep + print.charAt(0) + sep + print.charAt(1) + sep + print.charAt(2) + sep + print;
		String shorter = shortenUp(thePath);
		if(buxExist.get(shorter) == false) {
			// Double-check on the other side of a memory barrier
			synchronized(this) {
				if(buxExist.get(shorter) == false) {
					// OH SNAP time to make the directory
					if((new File(thePath)).mkdir() == false) {
						throw new CloudException("Unable to create directory " + thePath);
					}
					// And now mark it for posteriority
					buxExist.put(shorter, true);
				}
			}
		}
		return thePath;
	}
	
	
	public void prepop(String base) {
		prepop(base, 3);
	}
	
	private void prepop(String base, int max) {
		if(max ==0){
			return;
		}
		max--;

		for(int x=0; x < 16; x++){
			String pathy = base + File.separator + digits[x];
			buxExist.put(shortenUp(pathy), false);
			prepop(pathy, max);
		}
	}
	
	private String shortenUp(String path) {
		return path.substring(bloxPath.length());
	}

	@Override
	public DataStoreType getType() {
		return DataStoreType.cifs;
	}

}
