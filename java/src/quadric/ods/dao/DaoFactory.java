package quadric.ods.dao;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import quadric.blockvaulter.VaultSettings;
import quadric.legacy.NimbusVersionCrud;
import quadric.legacy.NimbusVmCrud;

/**
 * My job is to ensure only one instance of each DB's dao ever exists in memory
 *
 */
public class DaoFactory {

	private Map<String,Dao> cache = new HashMap<String,Dao>();
	private Map<String,Supplier<Dao>> suppliers = new HashMap<String,Supplier<Dao>>();
	
	private static DaoFactory me = new DaoFactory();
	
	public static DaoFactory instance() {
		return me;
	}

	private DaoFactory() {
		;
	}
	
	public void init() {		
		final String gfsLocation = VaultSettings.instance().getLocalDbPath() + File.separator + "gfs.db";
		suppliers.put(gfsLocation, () -> {
			List<Crud<?>> crudz = new ArrayList<Crud<?>>();
			crudz.add(new GfsVersionCrud());
			return new Dao(gfsLocation, crudz, true, false);
		});
		
		final String nimbusDbLoc = VaultSettings.instance().getLocalDbPath() + File.separator + "nimbusdb.db";
		suppliers.put(nimbusDbLoc, () -> {
			List<Crud<?>> crudz = new ArrayList<Crud<?>>();
			crudz.add(new NimbusVmCrud());
			crudz.add(new NimbusVersionCrud());
			return new Dao(nimbusDbLoc, crudz, true, false);
		});
		
		final String cacheDbLoc = VaultSettings.instance().getRemoteDbPath() + "/cache.db";
		suppliers.put(cacheDbLoc, () -> {
			List<Crud<?>> cruft = new ArrayList<Crud<?>>();
			cruft.add(new FlagCrud());
			cruft.add(new VmVersionCrud());
			cruft.add(new DsInfoCrud());
			cruft.add(new UuidPairCrud());
			return new Dao(cacheDbLoc, cruft, true, true);
		});
		
	}
	
	public Dao oneOff(String loc, List<Crud<?>> crudz, boolean useWall, boolean allowSimul) {
		return new Dao(loc, crudz, useWall, allowSimul);
	}
	
	public synchronized Dao create(String dbPath) {
		Dao d = cache.get(dbPath);
		if(d == null) {
			d = suppliers.get(dbPath).get();
			cache.put(dbPath, d);
		}
		return d;
	}
	


}
