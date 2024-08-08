package quadric.blockvaulter;

import java.util.HashMap;
import java.util.Map;

import quadric.ods.Ods;

public class DataStores {
	private static DataStores me = new DataStores();
	private DataStores() { ;}

	
	private Map<Integer, Ods> dataStores = new HashMap<Integer,Ods>();

	public static DataStores instance() {
		return me;
	}
	
	public synchronized Ods getOds(int siteId) {
		if(siteId == -1) {
			throw new CloudException("Invalid site id parameter");
		}
		Ods ds = dataStores.get(siteId);
		if(ds == null) {
			ds = new Ods(siteId);
			dataStores.put(siteId, ds);
		}
		return ds;
	}
	
	public synchronized int count() {
		return dataStores.size();
	}
	
}
