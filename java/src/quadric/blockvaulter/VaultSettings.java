package quadric.blockvaulter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.ods.CheckType;
import quadric.ods.dao.Crud;
import quadric.ods.dao.Dao;
import quadric.ods.dao.DaoFactory;
import quadric.ods.dao.SettingCrud;
import quadric.spdb.KurganBlock;
import quadric.spdb.KurganBlock.BlockSettings;
import quadric.spdb.UnifiedAdapter;
import quadric.util.BandwidthMeter;
import quadric.util.Pair;


public class VaultSettings {
	private static final Logger LOGGER = LoggerFactory.getLogger( VaultSettings.class.getName() );
	//private static final String WINE_DIR = "Z:\\home\\alike\\Alike\\ADS";
	private static final String NIMBUS_DB_NAME = "nimbusdb.db";
	
	private static VaultSettings me = new VaultSettings();
	
	private Map<String,String> settings = new HashMap<String,String>();
	private Map<Integer,CloudAdapter> adapters = new HashMap<Integer,CloudAdapter>();
	private BandwidthMeter meter = new BandwidthMeter();
	private String nimbusDbPath;
	private Properties props;
	
	
	private VaultSettings() { ;
	}
	
	public static VaultSettings instance() {
		return me;
	}
	
	public synchronized void debugInitialize(Map<String,String> forcedSettings) {
		this.settings = forcedSettings;
		setBandwidth();
	}
	
	public synchronized String initialize(String myPath, int setsId) throws java.io.IOException {
		LOGGER.debug( "Entering VaultSettings initialize");
		
		String polite = myPath + "/java/blockvaulter.properties";
		if(new File(polite).exists() == false) {
			throw new IOException("Property file " + myPath + " not found");
		}
		try (InputStream in = new FileInputStream(polite)) {
			props = new Properties();
			props.load(in);
		}
		DaoFactory.instance().init();
		settings.clear();
		//String myPath = System.getProperty("java.io.tmpdir");
		nimbusDbPath = myPath + File.separator + "DBs" + File.separator + NIMBUS_DB_NAME;
		if(new File(nimbusDbPath).exists()) {
			loadFromDb();
			myPath = getJvPath();
		} else {
			throw new CloudException("Cannot locate NimbusDB.db at " + nimbusDbPath);
		}
		
		setBandwidth();
		return myPath;
	}
	
	public synchronized long getVaultLeaseAgeMaxMs() {
		long timeout = 1000 * 60 * 60 * 5;		// Default to five hours
		String vile = settings.get("commitLeaseTimeoutMinutes");
		try {
			timeout = Integer.parseInt(vile);
			timeout = timeout * 60L * 1000L;	// Multiply by 60k to get from mins to MS
		} catch(Throwable t) { ; }
		return timeout;		
	}
	
	public synchronized String getNativeLibraryPath() {
		return props.getProperty("NATIVE_LIB_DIR");
	}
	
	public synchronized String getFlrMountBase() {
		return props.getProperty("FLR_MOUNT_BASE");
	}
	
	public synchronized int getSocketTimeoutSecs() {
		int timeout = 60 * 10;
		String vile = settings.get("socketTimeoutSecs");
		try {
			timeout = Integer.parseInt(vile);
		} catch(Throwable t) { ; }
		return timeout;		
	}
	
	public synchronized int getMungePort() {
		int port = 2811;
		String vile = settings.get("mungePort");
		try {
			port = Integer.parseInt(vile);
		} catch(Throwable t) { ; }
		return port;		
	}
	
	public synchronized int getMaintPurgeBatchSize(int siteId) {
		int sz = 500;
		String vile = settings.get("maintPurgeBatchSize" + siteId);
		try {
			sz = Integer.parseInt(vile);
		} catch(Throwable t) { ; }
		return sz;		
	}
	
	public synchronized int getMaxSocksPerClient() {
		int max = 50;
		String vile = props.getProperty("MAX_SOCKS_PER_CLIENT");
		try {
			max = Integer.parseInt(vile);
		} catch(Throwable t) { ; }
		return max;
	}
	
	
	/*public synchronized int getMaxMungeSockets() {
		int max = 0;
		String vile = settings.get("maxMungeSockets");
		try {
			max = Integer.parseInt(vile);
		} catch(Throwable t) { ; }
		return max;		
	}*/
	
	public synchronized int getJournalCommitInterval() {
		int max = 0;
		String vile = settings.get("journalCommitInterval");
		try {
			max = Integer.parseInt(vile);
		} catch(Throwable t) { ; }
		if(max == 0) {
			max = 10000;
		}
		return max;
	}
	
	public synchronized boolean getJournalConnectionUseWall() {
		String vile = settings.get("journalConnectionUseWall");
		if(vile == null) {
			return false;
		}
		if(vile.equals("true")) {
			return true;
		}
		return false;
	}
	
	public synchronized int getMaxFlrMounts() {
		int max = 10;
		String vile = settings.get("flrMaxMounts");
		try {
			max = Integer.parseInt(vile);
		} catch(Throwable t) { ; }
		return max;
	}
	
	public synchronized int getFlrTimeoutMins() {
		int max = 10;
		String vile = props.getProperty("FLR_TIMEOUT_MINS");
		try {
			max = Integer.parseInt(vile);
		} catch(Throwable t) { ; }
		return max;
	}
	
	public synchronized boolean isFuseMungeEnabled() {
		String vile = settings.get("useFuseMunge");
		if(vile == null) {
			return false;
		}
		if(vile.equals("true")) {
			return true;
		}
		return false;
	}
	
	public synchronized String getRestoreMountBase() {
		return props.getProperty("RESTORE_MOUNT_BASE");
	}
	
	public synchronized String getScratchMountBase() {
		return props.getProperty("SCRATCH_MOUNT_BASE");
	}
	
	public synchronized String getJvPath() {
		return props.getProperty("JV_PATH");
	}
	
	public synchronized long getTotalMemory() {
		return Runtime.getRuntime().maxMemory();
	}
	
	public synchronized boolean isForceMaintenance() { 
		String forcedStr = settings.get("isForcedMaintenance");
		if(forcedStr == null)  forcedStr = "";
		if(forcedStr.equals("true")) {
			return true;
		}
		return false;
	}
	
	public synchronized boolean isParanoid() {
		boolean paranoid = false;
		String paranoidStr = settings.get("paranoid");
		if(paranoidStr == null) paranoidStr = "";
		String paranoidStr2 = settings.get("paranoidBdb");
		if(paranoidStr2 == null) paranoidStr2 = "";
		if(paranoidStr.equals("true") || paranoidStr2.equals("true")) {
			paranoid = true;
		}
		return paranoid;
	}
	
	public synchronized boolean isParanoidEclDisabled(int siteId) {
		if(siteId  == 0) return false;
		String paranoidStr = settings.get("paranoidEclDisabled");
		if(paranoidStr == null) paranoidStr = "";
		return paranoidStr.equals("true");
	}
	
	public synchronized int getForceHeaderErrorRate() {
		int errorRate = 0;
		String rateStr = props.getProperty("FORCE_HEADER_ERROR_RATE");
		try {
			errorRate =  Integer.parseInt(rateStr);
		} catch(Throwable t) { ;}
		return errorRate;
	}
	
	public synchronized String getRemoteDbPath() {
		return props.getProperty("REMOTE_DBS_PATH");
	}
	
	public synchronized String getLocalDbPath() {
		return props.getProperty("LOCAL_DBS_PATH");
	}
	
	public synchronized String getAdsPath() {
		return props.getProperty("ADS_PATH");
	}
	
	public synchronized String getValidatePath() {
		return props.getProperty("VALIDATE_PATH");
	}
	
	public synchronized String getJobsPath() {
		return props.getProperty("JOBS_PATH");
	}
	
	public String getInterprocBase() {
		return props.getProperty("INTERPROC_PATH");
	}
		
	public boolean isLinux() {
		return System.getProperty("os.name").toLowerCase().contains("linux");
	}
	
	public String getInstallPath() {
		return getAlikeHome();
	}
	
	public synchronized String getTempPath(long len) {
		int cutOffMb = getTempPathCutoffMb();
		long cutOffBytes = ((long) cutOffMb) * 1024L * 1024L;
		if(len > cutOffBytes) {
			return getTempPathLarge();
		}
		return getTempPathSmall();
	}
	
	private int getTempPathCutoffMb() {
		String cutOffMbStr = settings.get("engineTempPathCutoffMb");
		if(cutOffMbStr == null) {
			cutOffMbStr = props.getProperty("TEMP_PATH_CUTOFF_MB");
		}
		int cutOffMb = Integer.parseInt(cutOffMbStr);
		return cutOffMb;
	}
	
	private String getTempPathSmall() {
		String shortPath = settings.get("engineTempPathSmall");
		if(shortPath == null) {
			return props.getProperty("TEMP_PATH");
		}
		return shortPath;
	}
	
	private String getTempPathLarge() {
		String largePath = settings.get("engineTempPathLarge");
		if(largePath == null) {
			return props.getProperty("TEMP_PATH_LARGE");
		}
		return largePath;
	}
	
	public synchronized String getAlikeHome() {
		return props.getProperty("ALIKE_HOME");
	}
	
	
	public synchronized CheckType getCheckType() {
		CheckType checkType;
		if(isParanoid()) {
			checkType = CheckType.full;
		} else {
			int val = 0;
			try {
				val = Integer.parseInt(VaultSettings.instance().getSettings().get("paranoidBlockCheckLevel"));
			} catch(Exception e) { ;}
			checkType = CheckType.values()[val];
		}
		return checkType;
	}
	
	public synchronized A2Share getShareConfig(int siteId) {
		A2Share s= new A2Share();
		if(siteId == 0) {
			s.setLocalPath("/mnt/ads");
		} else {
			s.setLocalPath("/mnt/ods1");
		}
		return s;
	}
	
	/*public synchronized boolean isReadOnlyTripped(int siteId) {
		 
	}*/
	
	/**
	 *  1: CIFS (blob), 2: NFS (blob), 3: Local Disk (blob), 4: S3FS (no blob), 5: Software S3 (no blob), 6: SSHFS (no blob),
	 *  7: Google Cloud FS (no blob), 8: Legacy LUCY CIFS (no blob), 9: CIFS no blob, 10: B2 fuse (no blob), 11: GlusterFS (no blobg), 12: NFS Raw, 13: Local Raw
	 * 
	 */
	public synchronized CloudAdapter getAdapter(int siteId) {
		CloudAdapter adp = adapters.get(siteId);
		if(adp != null) {
			return adp;
		}
		boolean useLocalTempForMeta = false;
		
		LOGGER.info("Creating new unified connection for site id " + siteId);
		adp = new UnifiedAdapter(siteId, useLocalTempForMeta);
		adp = new BandwidthCloudAdapter(adp, meter, "" + siteId, siteId);
		adapters.put(siteId, adp);
		return adp;
		
	}
	
	public synchronized Map<String,String> getSettings() {
		// Shallow copy
		return new HashMap<String,String>(settings);
	}
	
	public synchronized int getMaxConflictPrints(int dsNum) {
		String settingName = "maxConflictPrints" + dsNum;
		int num = 0;
		try {
			String cool = settings.get(settingName);
			num = Integer.parseInt(cool);
		} catch(Throwable t) { ;}
		if(num == 0) {
			num = 5000;
		}
		if(num != 5000) {
			LOGGER.info("OVERRIDING maxConflictPrints for datastore " + dsNum + " to " + num);
		}
		return num;
	}
	
	public synchronized int getDeleteThreadCount(int dsNum) {
		String settingName = "deleteThreadCount" + dsNum;
		int num = 0;
		try {
			String cool = settings.get(settingName);
			num = Integer.parseInt(cool);
		} catch(Throwable t) { ;}
		if(num == 0) {
			num = 5;
		} else {
			LOGGER.info("OVERRIDING delete thread count for datastore " + dsNum + " to " + num);
		}
		return num;
	}
	
	public synchronized int getReconFreq(int dsNum) {
		String settingName = "reconFreq" + dsNum;
		int num = 0;
		try {
			String cool = settings.get(settingName);
			num = Integer.parseInt(cool);
		} catch(Throwable t) { ;}
		if(num == 0) {
			num = 10;
		}
		if(num != 10) {
			LOGGER.info("OVERRIDING recon frequency for datastore " + dsNum + " to " + num);
		}
		return num;
	}
	
	public synchronized void reloadSettings() {
		Map<String,String> old = getSettings();
		settings.clear();
		loadFromDb();
		// We need to find settings related to their data stores
		// so we can reload their cloud adapters as needed
		Pattern p = Pattern.compile("\\d");
		Map<String,String> changed = findChangedKeys(settings, old);
		changed.forEach((k,v) -> { 
			if(v.startsWith("dataStore")) {
				Matcher m = p.matcher(v);
				int dsNum = 0;
				if(m.matches()) {
					dsNum = Integer.parseInt(m.group());
				}
				// Force this adapter to be reloaded
				adapters.remove(dsNum);
			}
		});
		setBandwidth();
	}
	
	/**
	 * Looks in the settings to determine the highest available datastore in use
	 * @return
	 */
	public synchronized int getDataStoreCount() {
		if(new File("/mnt/ods1/journals").exists()) {
			return 2;
		}
		return 1;
		
	}
	

	/** 
	 * @return
	 */
	public synchronized BlockSettings makeKurganSets() {
		int blockHeadSize = Integer.parseInt(settings.get("blockSize"));
		blockHeadSize *= 1024;
		KurganBlock.BlockSettings sex = new KurganBlock.BlockSettings();
		sex.blockSizeBytes = blockHeadSize;
		//String useEnc = settings.get("blockEncryption");
		
		String blockPassword = settings.get("blockPassword");
		if(blockPassword == null || blockPassword.isEmpty()) {
			;
		} else {
			sex.setupAesPassword(blockPassword);
			sex.shouldEncyptNewBlocks = true;
		}
		
		String legacyOffsitePassword = settings.get("legacyBlockPassword");
		if(legacyOffsitePassword == null || legacyOffsitePassword.isEmpty()) {
			;
		} else {
			sex.setupLegacyBlockPassword(legacyOffsitePassword);
		}
		
		String compStrStr = settings.get("compressionStr");
		if(compStrStr != null && compStrStr.length() > 0) {
			try {
				int compStr = Integer.parseInt(compStrStr);
				if(compStr > 0) {
					sex.shouldCompressNewBlocks = true;
				}
			} catch(Throwable t) { ; }
		}
		return sex;
	}
	
	
	
	@SuppressWarnings("unchecked")
	private Map findChangedKeys(Map m1, Map m2) {
		HashMap returnMe = new HashMap();
		m1.forEach((k,v) -> {
			Object foo = m2.get(k);
			if(foo != null) {
				if(v.equals(foo) == false) {
					returnMe.put(k,  foo);
				}
			}
		});
		return returnMe;
		
	}
	
	private void setBandwidth() {
		int uploadMb = 0; 
		try {
			uploadMb = Integer.parseInt(settings.get("offsiteBandwidthUploadMax"));
		} catch(Throwable t) {
			LOGGER.info("Bandwidth upload max not set; defaulting to open");
		}
		int downloadMb = 0;
		try {
			downloadMb = Integer.parseInt(settings.get("offsiteBandwidthDownloadMax"));
		} catch(Throwable t) { 
			LOGGER.info("Bandwidth download max not set; defaulting to open");
		}
		
		int uploadbytesSec = uploadMb * 1024 * 1024 / 8;
		int downloadbytesSec = downloadMb * 1024 * 1024 / 8;
		meter.getDownloadMeter().setMaxPerInterval(downloadbytesSec);
		meter.getUploadMeter().setMaxPerInterval(uploadbytesSec);
	}

	private void loadFromDb() {
		
		List<Crud<?>> crudz = new ArrayList<Crud<?>>();
		SettingCrud crud = new SettingCrud();
		crudz.add(crud);
		Dao dao = DaoFactory.instance().create(nimbusDbPath);
		LOGGER.info("Loading settings from database " + nimbusDbPath);
		try (Connection con = dao.getWriteConnection()) {
			crud.setConnection(con);
			List<Pair<String,String>> pairs = crud.query(crud.buildQuery("SELECT * FROM settings"));
			pairs.forEach(p -> settings.put(p.first, p.second));
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
		overlay();
		
	}
	
	private void overlay() {
		settings.put("blockPath", getAdsPath());
	}
		
}
