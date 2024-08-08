package quadric.blockvaulter;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.fuse.FuseMonitor;
import quadric.gfs.GfsManager;
import quadric.restore.FlrMapper;



/** 
 * The entrypoint for a filesystem-based block vaulter that receives commands via the filesystem and returns results via standard out
  * 
  * There is a small problem where if a client doesn't want to get a whole block but only a subset, we will still stream them 
  * the entire result back over stdout and they must complete the read in order to get anything else...
  * we could in theory chunk the writes in increments of some buffer size so they could cancel reading all of it by clobbering
  * the bucket in question with another request, but for us to do so we'd need to subchunk everything in smaller increments that
  * are pre-agreed upon by both parties with some control character afterwords to indicate whether we will continue streaming or
  * are awknoledging a cancel.
 */
public class BlockVaulter {
	private static  Logger LOGGER = null; 


	private MonitorSettings sets = new MonitorSettings();
	//private FuseMonitor restoreMonitor = new FuseMonitor();
	
	
	private KurganCommander commander = new KurganCommander();
	//private bool isIdle = true;
	

	public static void main(String [] args) {
		try {
			new File("/tmp/java.status").delete();
		} catch(Throwable t) { ;}
		try {
			new File("/tmp/jads.status").delete();
		} catch(Throwable t) { ;}

		LOGGER = LoggerFactory.getLogger( BlockVaulter.class.getName() );
		LOGGER.info("Starting BlockVaulter " + quadric.KurganBuildConstants.KURGAN_RELEASE_VERSION);
		if(args.length < 1) {
			LOGGER.error("Bad arguements");
			usage();
			return;
		}
		LOGGER.debug("Loading settings");
		BlockVaulter me = new BlockVaulter();
		String monitorDir = "";
		
		try {
			monitorDir = VaultSettings.instance().initialize(args[0], 0);
			int blockSizeKb = VaultSettings.instance().makeKurganSets().blockSizeBytes / 1024;
			LOGGER.info("USING ADS BLOCK SIZE " + blockSizeKb);
			if(VaultSettings.instance().makeKurganSets().shouldEncyptNewBlocks == true) {
				LOGGER.info("Backup data protected with AES 256-bit encyption. Your block password is REQUIRED if your A2 instance is lost or destroyed.");
			}
			int errorRate = VaultSettings.instance().getForceHeaderErrorRate();
			if(errorRate != 0) {
				LOGGER.warn("FORCE_HEADER_ERROR_RATE is nonzero, header errors will be FORCED. DO NOT RUN THIS IN RELEASE!");
			}
			File statusFile = new File("/tmp/java.status");
			statusFile.delete();
			File statusFile2 = new File("/tmp/jads.status");
			statusFile2.delete();
			LogUtils.setSyslogLevel();
			MonitorSettings sets = new MonitorSettings();
			sets.theDir = monitorDir;
			//sets.adp = adp;
			//sets.bucketCount = Integer.parseInt(args[1]);
			me.sets = sets;
			LOGGER.debug("About to init GfsManager....");
			GfsManager.instance().init();
			LOGGER.debug("About to init FLRMapper....");
			FlrMapper.instance().init();
			LOGGER.debug("About to init FuseMonitor....");
			FuseMonitor.instance().init();
			me.cleanup();
			me.commander.init();
			statusFile.createNewFile();
			
			LOGGER.debug("Status file created at " + statusFile.getPath());
			LOGGER.info("BlockVaulter started successfully."); 
			System.out.println("Java started successfully");
			LOGGER.debug("About to monitor path " + sets.theDir);
			me.monitor();
			LOGGER.debug("Classpath is: " + getClasspath());
		} catch(Exception e) {
			LOGGER.error("Unable to start up ", e);
			System.err.println(e);
		}

	}

	public static String getClasspath() {
		ClassLoader cl = BlockVaulter.class.getClassLoader();
		return getClasspath(cl);
	}
	
	public static String getClasspath(ClassLoader cl) {
		

		URL[] urls = ((URLClassLoader)cl).getURLs();
		
		StringBuilder builder = new StringBuilder();
		//builder.append(cl.toString());
		String delim = ",";
		for(URL url: urls){
			builder.append(url.getFile());
			builder.append(delim);
			
		}
		//builder.append("\n");
		if(cl.getParent() != null) {
			builder.append(getClasspath(cl.getParent()));
		}
		return builder.toString();
		
		//return System.getProperty("java.class.path");
	}
		
	public static void usage() {
		System.out.println("Usage:\nBlockVaulter path");
	}
	
	/**
	 * Monitor the path for fs changes and trigger vault action when changes occur
	*/
	public void monitor() {
		try {
			boolean logOnce = false;
			while(true) {
				try {
					Thread.sleep(500);
					File [] filez =  new File(sets.theDir).listFiles();
					if(filez == null) {
						// Directory not created
						if(logOnce == false) {
							LOGGER.info("Directory " + sets.theDir + " missing or not yet created");
							logOnce = true;
						}
					} else {
						logOnce = false;
						for(File f : filez) {
							int count = 0;
							while(f.length() == 0 && count < 1000) {
								try {
									// wait for phile to actually be written out 
									Thread.sleep(10);
									count++;
								} catch(InterruptedException ie) { ;}
							}
							trigger(f.getPath());
						}
					}
				} catch(Throwable e) {
					LOGGER.error("Error in monitor", e);
				} // end inner try
			} // end while
		} catch(Throwable e) {
			LOGGER.error("Fatal error in monitor", e);
		}
	}
	
	
	public void cleanup() {
		File [] filez =  new File(sets.theDir).listFiles();
		if(filez == null) {
			return;
		} 
		if(filez.length > 0) {
			LOGGER.info("Cleaning out " + filez.length + " old commands from trigger directory");
		}
		for(File f : filez) {
			f.delete();
		}
	}
	
	
	/**
	 * Trigger block action for a bucket at path
	*/
	public void trigger(String path) throws Exception { 
		if(commander.ownsCommand(path)) {
			// Fork these instructions over to another guy
			commander.trigger(path);
		}
		
	}
	
}

class MonitorSettings {
	String theDir;
}


	
	
