package quadric.blockvaulter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.fuse.FuseMonitor;
import quadric.ods.MaintenanceMaster;
import quadric.ods.Ods;
import quadric.ods.ResumeFile;
import quadric.ods.VaultCommandAdapter;
import quadric.restore.FlrMapper;
import quadric.spdb.CloudNotReadyException;
import quadric.stats.StatsManager;
import quadric.util.JobControl;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;

/**
 * A new and happy entrypoint for Kurgan filesystem commands
 * 
 * All commands are contained in a 
 * 
 * the following control files are used:
 * 
 * input:
 * (gorp).kg.breaker if present, kill the command
 * (gorp).kg.cmd json info about the command
 * 
 * Output:
 * (gorp).kg.result  results from the operation	
 * (gorp).kg.progress progress from the operation
 * 
 * the (gorp).kg.cmd file is json. It must have a top-level entry like:
 * 
 * "command": "commandname"
 * 
 * everything else is custom for the command type
 * 
 *
 */
public class KurganCommander {
	private static final Logger LOGGER = LoggerFactory.getLogger( KurganCommander.class.getName() );
	private static final int PROGRESS_UPDATE_WINDOW_MS = 266;
	private static final int JOB_MEMORY_COUNT = 50000;
	private static final int INITIAL_SYNC_LOG_INTERVAL_SECS_UI = 5;
	private static final int INITIAL_SYNC_LOG_INTERVAL_SECS_LOG = 120;
	
	public static class Dispatch extends JobControl implements DoubleConsumer {
		public String installId;
		public String jobGorp;
		public String json;
		private KurganCommander parent = null;
		public String result;
		public String dispatchDir;
		public long lastProgressTime = 0;
		public long startTime = System.currentTimeMillis();
		public double progress = 0;
		public ResumeFile resumeFile;
		
		@Override
		public String toString() {
			return "Dispatch id " + jobGorp + " command " + getJsonParam("command"); 
		}
				
		public String getJsonParam(String name) {
			return JsonUtil.getStringVal(name, json);
		}
		

		@Override
		public int hashCode() {
			return jobGorp.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Dispatch other = (Dispatch) obj;
			return other.jobGorp.equals(jobGorp);
		}
		
		
		@Override
		public void accept(double value) {
			if(parent == null) {
				return;
			}
			parent.progress(this, value);
		}		
		
		public String getInstallId() {
			return this.installId;
		}
		
		
	}
	
	private Map<Dispatch,Dispatch> dispatches = Collections.synchronizedMap(new HashMap<Dispatch,Dispatch>());
	private Map<String,Object> executedJobs = Collections.synchronizedMap(new LinkedHashMap<String,Object>() {
		 @Override
	     protected boolean removeEldestEntry(Map.Entry<String, Object> eldest)
	     {
	        return this.size() > JOB_MEMORY_COUNT;   
	     }
	  });
	
	private ExecutorService executor = Executors.newCachedThreadPool();
	private ExecutorService progExecutor = Executors.newSingleThreadExecutor();
	private Stopwatch initialSyncTimerUi = new Stopwatch();
	private Stopwatch initialSyncTimerLog = new Stopwatch();
	
	public KurganCommander() {; }
	
	public void init() {
		int dsCount = VaultSettings.instance().getDataStoreCount();
		List<Ods> dss = new ArrayList<Ods>();
		for(int x = 0; x < dsCount; ++x) {
			Ods ds = getOds(x);
			dss.add(ds);
		}
		executor.submit(() -> {
			initAll(dss);
		});
	}
	
	public boolean ownsCommand(String path) {
		if(path.toString().endsWith("kg.breaker") 
		|| path.toString().endsWith("kg.cmd")) {
			return true;
		}
		return false;
	}
	
	public void trigger(String path) {
		Path p = Paths.get(path);
		
		
		Dispatch d = new Dispatch();
		d.parent = this;
		try {
			d.installId = p.getFileName().toString().split("_")[0];
			d.jobGorp = p.getFileName().toString().split("\\.")[0];
			d.dispatchDir = p.getParent().toString();
		} catch(Exception e ) {
			LOGGER.error("Cannot parse incoming dispatch filename", e);
		}
		if(path.endsWith(".breaker")) {
			// Avoid repeat kills which throw npe
			Dispatch happy = dispatches.get(d);
			if(happy != null) {
				LOGGER.info("Canceling dispatch due to request breaker request at " + p.toString());
				kill(dispatches.get(happy));
			}
		} else if(path.endsWith(".cmd")) {
			if(executedJobs.containsKey(d.jobGorp)) {
				//LOGGER.trace("Ignoring repeatedly triggered dispatch");
				return;
			}
			LOGGER.debug( "Firing dispatch request at " + p.toString());
			// Determine json
			try {
				byte [] cruftface = Files.readAllBytes(p);
				d.json = new String(cruftface, "UTF-8");
			} catch (NoSuchFileException nsfe) {
				LOGGER.trace("Ignoring false alarm trigger");
				return;
			} catch(Exception e) {
				LOGGER.error("Cannot parse incoming dispatch message", e);
				return;
			}
			
			executedJobs.put(d.jobGorp, null);
			dispatches.put(d, d);
			// Execute all dispatches in a subthread
			executor.submit(() -> {
				dispatch(d);
			});
		} else { 
			LOGGER.trace("Ignoring dispatch command at " + p.toString());
		}
	}
	
	/**
	 * Waits for all dispatches to finish cleanly
	 */
	public void waitForAllComplete() {
		while(dispatches.isEmpty() == false) {
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				;
			}
		}
		LOGGER.debug( "Waited for all dispatches to complete, returning");
	}
	
	private Ods getOds(int id) {
		return DataStores.instance().getOds(id);
	}
	
	private void dispatch(Dispatch d) {
		LOGGER.debug("Entering dispatch for " + d.json);
		int siteId = -1;
		try {
			siteId = Integer.parseInt(d.getJsonParam("site"));
		} catch(Throwable t) { 
			;
		}
		try {
			switch(d.getJsonParam("command")) {
				case "commit" :
					VaultCommandAdapter.commit(getOds(siteId), d);
					break;
				case "vault":
					String sourceOdsStr = d.getJsonParam("source");
					
					int sourceId;
					try {
						sourceId = Integer.parseInt(sourceOdsStr);
					} catch(Throwable t) {
						throw new CloudException("Unable to determine source site for command " + d);
					}
					VaultCommandAdapter.vault(getOds(sourceId), getOds(siteId), d);
					break;
				case "delete": 
					VaultCommandAdapter.delete(getOds(siteId), d);
					break;
				case "deleteVm":
					VaultCommandAdapter.deleteVm(getOds(siteId), d);
					break;
				case "deleteVmRetainOnly":
					VaultCommandAdapter.deleteVmRetainOnly(getOds(siteId), d);
					break;
				case "validate":
					// Perform custom error handling to prevent unecessary stack messages
					try {
						VaultCommandAdapter.validate(getOds(siteId), d);
					} catch(CloudException ce) {
						d.result = "1:" + ce.getMessage();
						if(ce.getCause() != null) {
							appendTrace(d, ce.getCause());
						}
					}
					break;
				case "refCheck":
					VaultCommandAdapter.refCheck(getOds(siteId), d);
					break;
				case "hca":
					VaultCommandAdapter.createHcls(getOds(siteId), d);
					break;
				case "lockVersion":
					VaultCommandAdapter.lockVersion(getOds(siteId), d);
					break;
				case "restore":
					VaultCommandAdapter.restore(getOds(siteId),  d);
					break;
				case "resume":
					resume(d);
					break;
				case "settings":
					reloadSettings(d);
					break;
				case "shutdown":
					shutdown(d);
					break;
				case "status":
					status(d);
					break;
				case "forceUnregister":
					forceUnregisterMunges(d);
					break;
				case "verifyAds":
					VaultCommandAdapter.adsRebuildProgress(getOds(siteId),  d);
					break;
				case  "repair":
					VaultCommandAdapter.repair(d, 0, d);
					break;
				default:
					throw new CloudException("Unrecognized command \"" + d.getJsonParam("command") + "\"");
			}	
			if(d.result == null || d.result.isEmpty()) {
				d.result = "0:success";
			}
		} catch(CloudNotReadyException cne) {
			LOGGER.error("Unable to process dispatch; block generation not yet completed");
			d.result = "1:" + "Initial block generation not yet completed";
		} catch(Throwable e) {
			LOGGER.error("Error processing dispatch", e);
			d.result = "1:" + e.getMessage();
			appendTrace(d, e);
		} finally {
			end(d);
		}
		
	}
	
	private void appendTrace(Dispatch d, Throwable e) {
		d.result += System.lineSeparator();
		StringWriter sw = new StringWriter();
		try (PrintWriter pw = new PrintWriter(sw)) {
			e.printStackTrace(pw);
			d.result += sw.toString();
		}
	}
	
	private void initAll(List<Ods> dss) {
		try {
			String installId = VaultSettings.instance().getSettings().get("installID");
			if(installId == null) {
				LOGGER.error("Install id is empty in settings table!");
				return;
			}
			JobControl control = new JobControl();
			DoubleConsumer consume = (d) -> initialSyncProgress(d);
			VaultCommandAdapter.syncAll(dss, installId, consume, control);
		} catch(Exception e) {
			LOGGER.error("Problem syncing datastores", e);
		} catch(Throwable t) {;
			LOGGER.error("Problem syncing datastores", t);
		}
	}
	
	private void initialSyncProgress(Double d) {
		String awesome = String.format("%.4f", d);
		if(initialSyncTimerUi.getElapsed(TimeUnit.SECONDS) > INITIAL_SYNC_LOG_INTERVAL_SECS_UI) {
			try (FileOutputStream fos = new FileOutputStream("/tmp/jads.progress")) {
				fos.write(awesome.getBytes("US-ASCII"));
			} catch(Throwable t) {
				LOGGER.debug("Error updating initial sync", t);
			}
			initialSyncTimerUi.reset();
		}
		if(initialSyncTimerLog.getElapsed(TimeUnit.SECONDS) > INITIAL_SYNC_LOG_INTERVAL_SECS_LOG) {
			LOGGER.info("Initial sync " + awesome);
			initialSyncTimerLog.reset();
		}
	}
	
	private void reloadSettings(Dispatch d) {
		VaultSettings.instance().reloadSettings();
		d.accept(50);
		LogUtils.setSyslogLevel();
		d.accept(100);
	}
	
	
	
	private void shutdown(Dispatch d) {
		LOGGER.info("Initiating graceful shutdown");
		try {
			MaintenanceMaster.instance().shutdown();
		} catch(Throwable t) {
			LOGGER.error("Problem shutting down maintenance master", t);
		}
		try {
			StatsManager.instance().shutdown();
		} catch(Throwable t) {
			LOGGER.error("Problem shutting down stats", t);
		}
		try {
			JobControl.shutdown();
		} catch(Throwable t) {
			LOGGER.error("Problem shutting down job controls", t);
		}
		d.accept(25);
		try {
			FuseMonitor.instance().preshutdown();
		} catch(Throwable t) {
			LOGGER.error("Problem with pre-shutdown of FuseMonitor", t);
		}
		d.accept(50);
		try {
			FlrMapper.instance().shutdown();
		} catch(Throwable t) {
			LOGGER.error("Problem shutting down FLR mounts", t);
		}
		int count = DataStores.instance().count();
		for(int x = 0; x < count; ++x) {
			Ods ds = DataStores.instance().getOds(x);
			if(ds != null) {
				ds.shutdown();
			}
		}
		/* try {
			FuseMonitor.instance().shutdown();
		} catch(Throwable t) {
			LOGGER.error("Problem with shutdown of FuseMonitor", t);
		}*/
		d.accept(100);
	}
	
	
	
	
	private void forceUnregisterMunges(Dispatch d) {
		DataStores.instance().getOds(0).getVaultManager().forceUnregisterAll();
	}
	
	private void status(Dispatch d) {
		String output = d.getJsonParam("outputFile");
		SimpleDateFormat fd = new SimpleDateFormat("MM-dd HH:mm:ss");
		try (PrintWriter fw = new PrintWriter(new FileWriter(output))) {
			fw.println("Active Dispatches");
			fw.println("Start Time\tDispatch Id\tCommand\t%\tLast Update");
			for(Dispatch dd : dispatches.values()) {
				String shortName = dd.jobGorp.substring(0, 3) + ".." + dd.jobGorp.substring(dd.jobGorp.length() - 4);
				String commandName = dd.getJsonParam("command");
				String lastUpdate = fd.format(dd.lastProgressTime);
				String startTime = fd.format(dd.startTime);
				
				fw.print(startTime);
				fw.print("\t" + shortName);
				fw.print("\t" + commandName);
				fw.printf("\t%.2f", dd.progress);
				fw.println("%\t" + lastUpdate);
			}
			fw.println("Active Munges");
			fw.println("JobId\tvmId\tLast Updated");
			Map<String,Long> munges = DataStores.instance().getOds(0).getVaultManager().listActiveMunges();
			for(Map.Entry<String,Long> e : munges.entrySet()) {
				String [] splitz = e.getKey().split("_");
				fw.println(splitz[0] +  "\t" + splitz[1] + "\t" + fd.format(e.getValue()));
			}
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
	}
	
	private void resume(Dispatch d) {
		String resumeFileName = d.getJsonParam("path");
		if(resumeFileName == null || resumeFileName.equals("")) {
			throw new CloudException("Missing param 'path'");
		}
		ResumeFile resume = new ResumeFile(resumeFileName);
		// Replace the incoming JSON with the prexisting one from the resume file
		d.json = resume.getJsonHeader(); 
		d.resumeFile = resume;
		LOGGER.info("Resuming previous dispatch after intteruption, will dispatch it now");
		dispatch(d);
	}
	
	private void kill(Dispatch d) {
		d.cancel();
		if(d.result == null || d.result.equals("")) {
			d.result = "2:Canceled";
		}
		end(d);
	}
	
	private void progress(Dispatch d, double value) {
		if(value == 100) {
			// Progress of 100% is always updated
			d.lastProgressTime = 0;
		} 
		if(System.currentTimeMillis() - d.lastProgressTime <= PROGRESS_UPDATE_WINDOW_MS) {
			return;
		}
		d.lastProgressTime = System.currentTimeMillis(); 
		d.progress = value;
		// Don't hold up the party
		progExecutor.submit(() -> {
			try (PrintWriter fw = new PrintWriter(new FileWriter(d.dispatchDir + File.separator + d.jobGorp + ".kg.progress"))) {
				fw.printf("%.4f", value);
			} catch(IOException ioe) {
				;
			}
		});
	}
	
	private void end(Dispatch d) {
		String result = VaultUtil.prettyTruncate(d.result, 10);
		LOGGER.info("Completing dispatch " + d + " with result: " + result);
		try (FileOutputStream fw = new FileOutputStream(d.dispatchDir + File.separator + d.jobGorp + ".kg.result")) {
			byte[] stupid = d.result.getBytes("US-ASCII");
			fw.write(stupid);
		} catch(IOException ioe) {
			
			throw new CloudException(ioe);
		}
		dispatches.remove(d);
	}
	
	
}
