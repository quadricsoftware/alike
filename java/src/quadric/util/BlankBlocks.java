package quadric.util;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.crypt.CryptUtil;
import quadric.spdb.KurganBlock;
import quadric.vhdx.VirtualVhdx;

public class BlankBlocks {
	private static final Logger LOGGER = LoggerFactory.getLogger( BlankBlocks.class.getName() );
	private static BlankBlocks me = new BlankBlocks();
	private Map<Integer,Print> blanks = new HashMap<Integer,Print>();
	private Map<String,Integer> blanks2 = new HashMap<String,Integer>();
	
	private BlankBlocks() {
		LOGGER.debug("Initializing blank blocks");
		for(int x = 1; x < 40; ++x) {
			int sz = 1024 * 512 * x;
			byte [] bite = new byte[sz];
			String md5 = CryptUtil.makeMd5Hash(bite);
			blanks.put(sz, new Print(md5));
			blanks2.put(md5.toLowerCase(), sz);
		}
		LOGGER.debug("Blank blocks are " + blanks2.toString());
	}
	
	public Print getBlank(int len) {
		return blanks.get(len);
	}
	
	public boolean isBlank(String print) {
		if(blanks2.containsKey(print.toLowerCase())) {
			return true;
		}
		return false;
	}
	
	public static BlankBlocks instance() {
		return me;
	}
	
	
}
