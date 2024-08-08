package quadric.ods;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import quadric.blockvaulter.A2Share;
import quadric.blockvaulter.BandwidthCloudAdapter;
import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.DataStores;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.KurganCommander;
import quadric.blockvaulter.KurganCommander.Dispatch;
import quadric.blockvaulter.VaultSettings;
import quadric.fuse.AmbMonitor.AmbStats;
import quadric.gfs.GfsManager;
import quadric.legacy.NimbusDbListener;
import quadric.legacy.NimbusVersion;
import quadric.ods.HeaderManager.HeaderEvent;
import quadric.ods.HeaderManager.HeaderEventType;
import quadric.ods.Ods.SyncException;
import quadric.ods.Ods.VaultTx;
import quadric.restore.VersionLocker;
import quadric.restore.VmVersionKey;
import quadric.spdb.ConsistencyException;
import quadric.spdb.KurganBlock;
import quadric.spdb.KurganBlock.BlockSettings;
import quadric.util.HclCursor;
import quadric.util.HclReaderUtil;
import quadric.util.HclWriterUtil;
import quadric.util.JobControl;
import quadric.util.Pair;
import quadric.util.PathedGetResult;
import quadric.util.Print;
import quadric.util.Stopwatch;
import quadric.util.ValidationHelper;
import quadric.util.VaultUtil;

/**
 * An adapter layer between KurganCommander and the ODS system. Also converts inputs to ECLs and handles job directory
 * 
 *
 */
public class VaultCommandAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( VaultCommandAdapter.class.getName() );
	private static final long RETRY_TIMEOUT_MS = 20 * 1000;
	//private static final long RETRY_TIMEOUT_MS = 60 * 1000 * 3;
	private static final long RETRY_SLEEP_MS = 1000 * 3;
	public static final int MAX_META_FILE_SIZE = 1024 * 1024 * 4;
	private static final int MAX_ECL_THREADS = 3;
	private static final int RESUME_FILE_FLUSH_SECS = 30;
	private static final int MAX_REPAIR_EFFORT_MINS = 3;
	public static final String CMD_FS_SYNC = "/bin/sync";
	private static boolean requiredRebuild = false;
	 
	

	
	/**
	 * Commits data from a "jobs" directory to a datastore
	 * "source": the source directory
	 * @param ds which datastore to send to
	 * @param d the command with details
	 */
	public static NimbusVersion commit(Ods ds, KurganCommander.Dispatch d) {
		String source = d.getJsonParam("source");
		if(source == null || source.equals("")) {
			throw new CloudException("Missing parameter 'source'");
		}
		String isImportStr = d.getJsonParam("import");
		boolean isImport = false;
		if(isImportStr.equals("true")) {
			LOGGER.debug("Import mode activated for commit from source " + source);
			isImport = true;
		}
		
		LOGGER.debug( "Received dispatch to commit at source path " + source);
		JobAmbDesc desc;
		String eclLoc;
		try {
			if(d.resumeFile != null) {
				LOGGER.debug( "Resume mode enabled");
				desc = new JobAmbDesc(d.resumeFile);
			} else {
				desc = new JobAmbDesc(source);
				LOGGER.info("Job " + desc.jobId + " has " 
						+ desc.hcls.size() + " HCLs to process");
			}
			
			eclLoc = VaultSettings.instance().getValidatePath() + File.separator 
					+ desc.jobId 
					+ "_" + desc.vmId 
					+ ".ecl";
			if(d.resumeFile == null) {
				if(new File(eclLoc).exists()) { 
					LOGGER.debug("Overwriting existing ECL at path " + eclLoc);
					new File(eclLoc).delete();
				} 
				LOGGER.debug( "Creating ECL for commit data at path " + eclLoc);
				createEcl(desc, eclLoc, VaultUtil.nestedProgress(d, 0, 2), d);
			}
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		
		LOGGER.debug( "ECL ready for job " + desc.jobId + " and VM " + desc.vmId);
		boolean completedOk = false;
		BlockSource bs = null;
		if(isImport == false) {
			bs = new NoOpBlockSource(ds.getSiteId());
		} else {
			String bdbPath = d.getJsonParam("bdbPath");
			String encPass = d.getJsonParam("importEncPass");
			bs = new RlogBlockSource(bdbPath, desc.getRlogs(), encPass);
		}
		MetaGarbage garbage = null;
		NimbusVersion vers = null;
		try {
		//try (AmbBlockSource bs = new AmbBlockSource(destAmbs, eclLoc)) {
			LOGGER.debug("Creating meta for commit...");
			garbage = new MetaGarbage(d.getInstallId(), desc.jobId, desc.vmId);
			LOGGER.debug("Commit about to doVault...");
			vers = doVault(eclLoc, garbage, bs, ds, d, d, VaultUtil.nestedProgress(d, 2, 98)).first;
			// This BS is about data deduplication stats only
			completedOk = true;
			return vers;
	 	} finally {
			LOGGER.info("Cleaning up resources for job " + d);
			// Only on success delete AMBs and ECL
			if(d.isCanceled() || completedOk) {
				if(new File(eclLoc).delete() == false) {
						LOGGER.error("Unable to delete resource at " + eclLoc);
				}
				// Unregister the munge operation
				Object stats = ds.getVaultManager().unregisterMunge(garbage);
				if(isImport && stats == null) {
					// Just default to nothing
					stats = new AmbStats();
				}
				if(vers != null) {
					updateNimbusStats(vers, (AmbStats) stats);
				}
			} else {
				LOGGER.info("Leaving ECL " + eclLoc + " after job failure for job " + d);
			}
			if(bs instanceof Closeable) {
				try {
					((Closeable) bs).close();
				} catch (IOException e) { throw new CloudException(e);}
			}
		}
	}
	
	/**
	 * Vaults data from one DS to another
	 */
	public static void vault(Ods source, Ods dest, KurganCommander.Dispatch d) {
		if(dest.isReadOnly()) {
			throw new CloudException("ODS target is read-only");
		}
		String versionStr = d.getJsonParam("version");
		String uuid = d.getJsonParam("uuid");
		String vmIdStr = d.getJsonParam("vm");
		String jobIdStr = d.getJsonParam("job");
		
		
		long version;
		long jobId;
		long vmId;
		try {
			version = Long.parseLong(versionStr);
			jobId = Long.parseLong(jobIdStr);
			vmId = Long.parseLong(vmIdStr);
		} catch(Throwable t) {
			throw new CloudException("Unable to parse command " + d);
		}
		PathedGetResult rez = source.getEcl(uuid, version, VaultUtil.nestedProgress(d, 0, 1));
		MetaGarbage garbage = new MetaGarbage(d.getInstallId(), jobId, vmId);
		try (InputStream fis = rez.in) {
			CloudBlockSource cbs = new CloudBlockSource(source.getCloudAdapter());
			Pair<NimbusVersion,AmbStats> nicePair = doVault(rez.localPath, garbage, cbs, dest, d, d, VaultUtil.nestedProgress(d, 2, 97));
			updateJobStats(jobId, nicePair.second);
			repair(d, MAX_REPAIR_EFFORT_MINS, VaultUtil.nestedProgress(d, 99, 1));
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		
	}
	
	public static void adsRebuildProgress(Ods ds, KurganCommander.Dispatch d) {
		d.accept(.1);
		while(new File("/tmp/jads.status").exists() == false) {
			if(ds.isRebuildDead(d.installId)) {
				throw new CloudException("A2 service restart or reboot required after serious intermittency");
			}
			d.accept(ds.revealHeaderManager().rebuildProgress());
			d.control();
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) { ; }
		}
		d.accept(100);
	}
	
	/**
	 * Syncs all datastores in order
	 */
	public static void syncAll(List<Ods> dss, String installId, DoubleConsumer progress, JobControl control) {
		int x = 0;
		double stepPercent = 0; 
		if(dss.size() > 0) {
			stepPercent = 100.00 / ((double) dss.size());  
		} else {
			return;
		}
		for(Ods ds : dss) {
			double current = ((double) x) / ((double) dss.size()) * 100.00;
			DoubleConsumer dp = VaultUtil.nestedProgress(progress, current, stepPercent);
			LOGGER.info("Syncing data store " + x);
			try {
				boolean localSyncNeeded = ds.sync(installId, dp, control);
				if(localSyncNeeded == true) {
					requiredRebuild = true;
				}
			} catch(Throwable t) {
				LOGGER.error("Unable to sync datastore " + x + ". It will not be available. ", t);
			}
			++x;
		}
		// Completely resync our databases, justin case
		HeaderEvent he = new HeaderEvent();
		he.type = HeaderEventType.fullSync;
		NimbusDbListener.instance().accept(he);
		NimbusDbListener.instance().flush();
		File statusFile2 = new File("/tmp/jads.status");
		try {
			statusFile2.createNewFile();
		} catch(Exception e) { ;}
		
	}
	
	public static void deleteVm(Ods ds, KurganCommander.Dispatch d) {
		if(ds.isReadOnly()) {
			throw new CloudException("Read-only mode is active");
		}
		LOGGER.trace("Entering deleteVm for " + d);
		// DON'T "fix" uuid for this operation, as that will make it match nothing
		String uuid = d.getJsonParam("uuid");
		List<VmVersion> guys = ds.revealHeaderManager().listVersionsFromUuid(uuid);
		for(VmVersion v : guys) {
			doDelete(ds, d, uuid, v.getVersion());
		}
	}
	
	public static void deleteVmRetainOnly(Ods ds, KurganCommander.Dispatch d) {
		if(ds.isReadOnly()) {
			throw new CloudException("Read-only mode is active");
		}
		LOGGER.trace("Entering deleteVm for " + d);
		// DON'T "fix" uuid for this operation, as that will make it match nothing
		String uuid = d.getJsonParam("uuid");
		String retainedString = d.getJsonParam("retained");
		int retainedCount = Integer.parseInt(retainedString);
		List<VmVersion> guys = ds.revealHeaderManager().listVersionsFromUuid(uuid);
		guys.sort(Comparator.comparing(VmVersion::getVersion));
		int deletedCount = 0;
		for(VmVersion v : guys) {
			if(guys.size() - deletedCount <= retainedCount) {
				break;
			}
			doDelete(ds, d, uuid, v.getVersion());
			++deletedCount;
			
		}
	}
	
	/**
	 *  Deletes a ds version
	 * @param ds the data store to delete from
	 * @param d the dispatch
	 */
	public static void delete(Ods ds, KurganCommander.Dispatch d) {
		if(ds.isReadOnly()) {
			throw new CloudException("Read-only mode is active");
		}
		LOGGER.trace("Entering delete for " + d);
		String versionStr = d.getJsonParam("version");
		String uuid = d.getJsonParam("uuid");
		long version;
		try {
			version = Long.parseLong(versionStr);
		} catch(Throwable t) {
			throw new CloudException("Unable to parse version epoch time from command " + d);
		}
		doDelete(ds, d, uuid, version);
	}
	
	private static void doDelete(Ods ds, Dispatch d, String uuid, long version) {
		try {
			ds.makeDelete(d.getInstallId(), uuid, version, d, d);
		} catch(SyncException se) {
			ds.sync(d.getInstallId(), VaultUtil.nestedProgress(d, 0, 30), d);
			try {
				ds.makeDelete(d.getInstallId(), uuid, version, VaultUtil.nestedProgress(d, 30, 70), d);
			} catch(SyncException e) {
				throw new CloudException("Unable to sync due to " + e.getMessage());
			}
		}
		// Kill this version from the GFS database
		VmVersion vv = new VmVersion();
		vv.setSiteId(ds.getSiteId());
		vv.setVirtualType(VmVersion.guessVirtType(uuid));
		vv.setPlatformStyleUuid(uuid);
		vv.setVersion(version);
		String awesome = d.getJsonParam("doGfsPurge");
		if(awesome != null && awesome.isEmpty() == false) {
			GfsManager.instance().unmarkVersion(vv);
		}
		MaintenanceMaster.instance().register(d, ds.getSiteId());
	}
	
	public static void refCheck(Ods ds, KurganCommander.Dispatch d) {
		// Force recon 1st
		MaintenanceMaster.instance().register(d, ds.getSiteId());
		MaintenanceMaster.instance().force(ds.getSiteId());
		Pair<Long,Long> nicePair;
		
		ds.sync(d.installId, d, d);
		nicePair = ds.refCheck(d.installId); 
		
		ObjectMapper mapper = new ObjectMapper();
		try {
			String json = mapper.writeValueAsString(nicePair);
			String path = d.getJsonParam("outputFile");
			Files.write(Paths.get(path), json.getBytes("US-ASCII"));
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		
	}
	/**
	 *  Validates a ds version
	 * @param ds the data store to delete from
	 * @param d the dispatch
	 */
	public static void validate(Ods ds, KurganCommander.Dispatch d) {
		LOGGER.trace("Entering validate for " + d);
		String versionStr = d.getJsonParam("version");
		String uuid = d.getJsonParam("uuid");
		long version;
		try {
			version = Long.parseLong(versionStr);
		} catch(Throwable t) {
			throw new CloudException("Unable to parse version epoch time from command " + d);
		}
		List<Print> printsBatch = new ArrayList<Print>();
		List<Print> badBlocks = new ArrayList<Print>();
		List<Print> missingBlocks = new ArrayList<Print>();
		List<Exception> errorz = new ArrayList<Exception>();
		List<Print> errorPrints = new ArrayList<Print>();
		DoubleConsumer prog2 = VaultUtil.nestedProgress(d, 1, 100);
		LOGGER.debug("Validate is loading ecl for uuid " + uuid + " and version " + version);
		PathedGetResult rez = ds.getEcl(uuid, version, VaultUtil.nestedProgress(d, 0, 1));
		try (InputStream useless = rez.in) {
			EclReader reader = new EclReader(rez.localPath);
			HclCursor curses = reader.createGlobalCursor();
			int tots = curses.count();
			int curr = 0;
			int badTotal = 0;
			int batchSize = 10;
			ValidationHelper helper = new ValidationHelper();
			while(curses.hasNext()) {
				while(curses.hasNext()) {
					Print p = curses.next();
					printsBatch.add(p);
					if(printsBatch.size() > batchSize) {
						break;
					}
				}
				VaultUtil.reduce(3, () ->
					printsBatch.parallelStream().forEach(f -> {
						try {
							helper.doValidateBlock(f, ds);
						} catch(ConsistencyException ce) {
							LOGGER.error("Validation of block " + f + " failed due to " + ce.getMessage());
							badBlocks.add(f);
						} catch(FileNotFoundException mis) {
							LOGGER.error("Validation of block " + f + " failed due to missing block");
							missingBlocks.add(f);
						} catch(Exception e) {
							LOGGER.error("Validation of block " + f + " failed", e);
							errorz.add(e);
							errorPrints.add(f);
						}
				}));
				ds.revealBadBlockManager().mark(badBlocks);
				ds.revealBadBlockManager().mark(missingBlocks);
				if(missingBlocks.size() + badBlocks.size() + errorPrints.size() != badTotal) {
					helper.dumpValidateReport(d, badBlocks, errorPrints, missingBlocks);
					badTotal = badBlocks.size() + errorPrints.size() + missingBlocks.size();
				}
				curr+= printsBatch.size();
				printsBatch.clear();
				double prog = ((double) curr) / ((double) tots) * 100.00;
				prog2.accept(prog);
			}
		} catch(IOException ioe) { 
			throw new CloudException(ioe);
		}
		if(badBlocks.size() > 0) {
			throw new CloudException("Validation found " + badBlocks.size() + " damaged blocks on disk!");
		}
		if(missingBlocks.size() > 0) {
			throw new CloudException("Validation found " + missingBlocks.size() + " missing blocks on disk!");
		}
		if(errorz.size() > 0) {
			throw new CloudException("Validation encounted errors during run. " + errorz.size() + " blocks could not be validated.", errorz.get(0));
		}
		prog2.accept(100.00);
	}
		
	
	
	public static void restore(Ods ds, Dispatch d) {
		ds.sync(d.installId, VaultUtil.nestedProgress(d, 0, 1), d);
		String versionStr = d.getJsonParam("version");
		String uuid = d.getJsonParam("uuid");
		//String destPath = d.getJsonParam("dest");
		String destPath = VaultSettings.instance().getValidatePath() + File.separator + uuid;
		if(new File(destPath).exists() == false) {
			new File(destPath).mkdir();
		}
		String ext = "img";
		long version;
		try {
			version = Long.parseLong(versionStr);
		} catch(Throwable t) {
			throw new CloudException("Unable to parse version epoch time from command " + d);
		}
		
		try {
			LOGGER.debug( "Loading ecl for uuid " + uuid + " and version " + version);
			PathedGetResult rez = ds.getEcl(uuid, version, VaultUtil.nestedProgress(d, 1, 1));
			
			final List<Exception> problems = Collections.synchronizedList(new ArrayList<Exception>());
			try (InputStream wasteoid = rez.in) {
				EclReader reader = new EclReader(rez.localPath);
				int county = reader.getDisks().size();
				// In hindsight, using executors is MUCH MUCH more annoying than using lambda forEach
				ExecutorService executor = Executors.newFixedThreadPool(county);
				if(county > MAX_ECL_THREADS) county = MAX_ECL_THREADS;
				DoubleConsumer proggy = VaultUtil.nestedProgress(d, 2, 97.5);
				final BlockSettings sexxy =  VaultSettings.instance().makeKurganSets();
				for(int x = 0; x < county; ++x) {
					final int foobar = x;
					executor.submit(() -> {
						String shotputPath = destPath + File.separator + foobar + "." + ext;
						
						try (FileOutputStream buffy = new FileOutputStream(shotputPath)){
							HclCursor curses = reader.createCursor(foobar);
							
							while(curses.hasNext()) {
								Print p = curses.next();
								GetResult rezzy = ds.getCloudAdapter().getBlock(p.toString(), 0);
								try (InputStream killMe = rezzy.in) {
									byte [] bites = new byte[(int) rezzy.len];
									rezzy.in.read(bites);
									KurganBlock blockHead = new KurganBlock(bites, bites.length, p.toString());
									buffy.write(blockHead.unpackage(sexxy));
								}
								double awesome = ((double) curses.getPosition() / (double) curses.count()) * 100.00;
								proggy.accept(awesome);
								d.control();
								if(problems.size() > 0) {
									break;
								}
							}
							d.control();
						} catch(Exception ee) {
							problems.add(ee);
						}
					});
				}
				// This BS with executors is stupid
				executor.shutdown();
				d.control();
				while(true) {
					try {
						if(executor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
							break;
						}
					} catch(InterruptedException ie) { ;}
					d.control();
				}
				// Now this is the real executor kick in the pants...exceptions
				if(problems.size() > 0) {
					// Barfaroni
					throw new CloudException(problems.get(0));
				}
				
				d.accept(100);
			}
		} catch(CloudException ce) {
			throw ce;
		} catch(Exception e) { 
			throw new CloudException(e);
		}
	}
	
	
	/**
	 * Locks a version for restore.
	 * 
	 * Params are "uuid", "version", and "lockFile"
	 */
	public static void lockVersion(Ods ds, KurganCommander.Dispatch d) {
		String versionStr = d.getJsonParam("version");
		long version;
		try {
			version = Long.parseLong(versionStr);
		} catch(Throwable t) {
			throw new CloudException("Unable to parse version epoch time from command " + d);
		}
		String uuid = d.getJsonParam("uuid");
		String lockFile = d.getJsonParam("lockFile");
		VmVersionKey k = new VmVersionKey();
		k.setSiteId(ds.getSiteId());
		k.setUuid(uuid);
		k.setVersion(version);
		VersionLocker.instance().put(k, lockFile);
	}
	
	
	/**
	 * Creates HCLs in the the directory specified by "dest" with the extension specified by "ext" (no dot please) 
	 * for version epoch "version" and VM uuid "uuid" 
	 * 
	 */
	public static void createHcls(Ods ds, KurganCommander.Dispatch d) {
		ds.sync(d.installId, VaultUtil.nestedProgress(d, 0, 2), d);
		String versionStr = d.getJsonParam("version");
		String uuid = d.getJsonParam("uuid");
		//String destPath = VaultSettings.instance().cleanWinePath(d.getJsonParam("dest"));
		String destPath = d.getJsonParam("dest");
		String ext = d.getJsonParam("ext");
		long version;
		try {
			version = Long.parseLong(versionStr);
		} catch(Throwable t) {
			throw new CloudException("Unable to parse version epoch time from command " + d);
		}
		try {
			LOGGER.debug( "Loading ecl for uuid " + uuid + " and version " + version + " and site " + ds.getSiteId());
			PathedGetResult rez = ds.getEcl(uuid, version, VaultUtil.nestedProgress(d, 2, 18));
			
			final List<Exception> problems = Collections.synchronizedList(new ArrayList<Exception>());
			try (InputStream wasteoid = rez.in) {
				EclReader reader = new EclReader(rez.localPath);
				int county = reader.getDisks().size();
				// In hindsight, using executors is MUCH MUCH more annoying than using lambda forEach
				ExecutorService executor = Executors.newFixedThreadPool(county);
				if(county > MAX_ECL_THREADS) county = MAX_ECL_THREADS;
				DoubleConsumer proggy = VaultUtil.nestedProgress(d, 20, 79.5);
				
				for(int x = 0; x < county; ++x) {
					final int foobar = x;
					executor.submit(() -> {
						String shotputPath = destPath + File.separator + foobar + "." + ext;
						try {
							HclCursor curses = reader.createCursor(foobar);
							LOGGER.info("Generating HCL to dest path: " + shotputPath);
							HclWriterUtil writer = new HclWriterUtil(shotputPath, 0, true);
							while(curses.hasNext()) {
								writer.writePrint(curses.next());
								double awesome = ((double) curses.getPosition() / (double) curses.count()) * 100.00;
								proggy.accept(awesome);
								d.control();
								if(problems.size() > 0) {
									break;
								}
							}
							writer.writeCoda();
							d.control();
						} catch(Exception ee) {
							problems.add(ee);
						}
					});
				}
				// This BS with executors is stupid
				executor.shutdown();
				d.control();
				while(true) {
					try {
						if(executor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
							break;
						}
					} catch(InterruptedException ie) { ;}
					d.control();
				}
				// Now this is the real executor kick in the pants...exceptions
				if(problems.size() > 0) {
					// Barfaroni
					throw new CloudException(problems.get(0));
				}
				
				d.accept(100);
			}
		} catch(CloudException ce) {
			throw ce;
		} catch(Exception e) { 
			throw new CloudException(e);
		}
		
	}
	
	private static Pair<NimbusVersion,AmbStats> doVault(String ecl, MetaGarbage garbage, BlockSource blox, Ods ds, Dispatch d, JobControl c, DoubleConsumer progress) {
		LOGGER.debug( "Entering doVault for job id " + garbage.jobId + " and VM " + garbage.vmId);
		int concurrencyLevel = 4;
		String concurrencyLevelString = VaultSettings.instance().getSettings().get("dsConcurrencyLevel");
		if(concurrencyLevelString != null) {
			concurrencyLevel = Integer.parseInt(concurrencyLevelString);
		}
		
		NimbusVersion returnMe = null;
		CloudAdapter adp = ds.getCloudAdapter();
		List<VaultTx> vaulters;
		AtomicLong globalBytes = new AtomicLong();
		AtomicLong globalBlocks = new AtomicLong();
		boolean localRequiredRebuild = false;
		try {
			vaulters = ds.makeVault(garbage, ecl, blox, concurrencyLevel, VaultUtil.nestedProgress(progress, 0, 2), c);
		} catch(SyncException se) {
			localRequiredRebuild = ds.sync(garbage.installId, VaultUtil.nestedProgress(progress, 0, 2), c);
			if(localRequiredRebuild == true) {
				requiredRebuild = true;
			}
			try {
				vaulters = ds.makeVault(garbage, ecl, blox, concurrencyLevel, VaultUtil.nestedProgress(progress, 0, 2), c);
			} catch(SyncException se2) {
				throw new CloudException("Retry of sync failed: " + se.getMessage());
			}
		} 
		c.control();
		MaintenanceMaster.instance().register(d, ds.getSiteId());
		// Process VaultTxs in parallel to scale I/O
		DoubleConsumer nested2 = VaultUtil.nestedProgress(progress, 2, 99.98);
		int resumeTx = -1;
		if(requiredRebuild == false) {
			if(d.resumeFile != null) {
				LOGGER.debug( "Registering resumed transaction");
				resumeTx = ds.getVaultManager().register(d.resumeFile, c, ds.getSiteId());
			} 
		} else if(d.resumeFile != null) {
			LOGGER.info("Cannot optimize resumed vault because a rebuild was required");
		}
		if(vaulters.size() == 0) {
			LOGGER.debug( "No vaulters needed for this round, nothing will be unregistered either");
		} else {
			returnMe = (NimbusVersion) vaulters.get(0).info;
			long thisTxScope = vaulters.get(0).getTx();
			int totalPrints = blox.count();
			int written = 0;
			LOGGER.info("Beginning send phase of journal for job " + c);
			ForkJoinPool forkyAndBess = new ForkJoinPool(concurrencyLevel);
			final List<VaultTx> tomFool = vaulters;
			final ResumeFile resume = new ResumeFile(d, returnMe, thisTxScope);
			long t1 = System.nanoTime();
			final List<Exception> errorz = Collections.synchronizedList(new ArrayList<Exception>());
			/*A2Share share = VaultSettings.instance().getShareConfig(ds.getSiteId());
			boolean isA2Fs = false;
			if(share.getType() == 4) {
				isA2Fs = true;
			}*/
			//final boolean isA2FsReal = isA2Fs;
			try {
				forkyAndBess.execute( () ->
				tomFool.parallelStream().forEach(tx -> {
					// Scope the VaultTx because it's Closeable
					try (VaultTx tmp = tx) {
						long localTotal = 0;
						long localCnt = 0;
						while(true) {
							c.control();
							Print p = tx.vaultNext();
							if(p == null) {
								break;
							}
							localTotal += sendPrint(p, adp, blox, c);
							localCnt++;
							resume.enqueue(p);
							if(errorz.size() > 0) {
								// If any other thread died, we die
								return;
							}
						}
						// Finish him
						DoubleConsumer dummy = new Dispatch();
						tx.commit(dummy);
						globalBytes.addAndGet(localTotal);
						globalBlocks.addAndGet(localCnt);
					} catch(Exception ioe) { 
						errorz.add(ioe);
					}
				}));
				forkyAndBess.shutdown();
			
				// While we are waiting, we can poop out which prints we've successfully completed
				try {
					Stopwatch watchy = new Stopwatch();
					while(forkyAndBess.awaitTermination(200, TimeUnit.MILLISECONDS) == false) {
						if(watchy.getElapsed(TimeUnit.SECONDS) < RESUME_FILE_FLUSH_SECS) {
							// Sync disks
							try {
								VaultUtil.ezExec(CMD_FS_SYNC);
							} catch(IOException ioe) {
								throw new CloudException(ioe);
							}
							resume.dumpVaultedPrints();
							watchy.reset();
						}
						// Update progress
						written = resume.getVaultedCountEst();
						double divisor = ((double) totalPrints);
						if(divisor  == 0) {
							divisor = 1;
						}
						double prog = ((double) written) / divisor * 100.00; 
						nested2.accept(prog);
					} // end "while"
				} catch(InterruptedException iie) {; }
				if(errorz.size() > 0) {
					Exception ee = errorz.get(0);
					// Don't double-wrap that cruft
					if(ee instanceof CloudException) {
						throw (CloudException) ee;
					}
					throw new CloudException(errorz.get(0));
				}
				// Sync disks to secure vaulted data
				try {
					VaultUtil.ezExec(CMD_FS_SYNC);
				} catch(IOException ioe) {
					throw new CloudException(ioe);
				}
				long t2 = System.nanoTime();
				double myCoolTime = ((double) t2 - t1) / (1000000.00D);
				double thruput = ((double) globalBytes.get()) / myCoolTime; 
				double thruputMbSecs = thruput * 1000 / 1024 / 1024; 
				LOGGER.info("Sent " + globalBytes.get() + " bytes in " + String.format("%.2f", myCoolTime) 
												+ "ms; throughput is " +  String.format("%.2f", thruputMbSecs) + "MB/s");
			} finally {
				// We are done with this file. Clean it up.
				try {
					if(resume != null) {
						resume.delete();
					}
				} finally {
					if(resumeTx != -1) {
						// Only do this if there was a resumeTx
						ds.getVaultManager().unregister(resumeTx);
					}
				}
			}
			progress.accept(100);
		}
		AmbStats staties = new AmbStats();
		staties.blocksSent[0] = (int) globalBlocks.get();
		staties.bytesSent[0] = globalBytes.get();
		staties.bytesSkipped[0] = returnMe.getTotalSize() - globalBytes.get();
		staties.blocksSkipped[0] = new EclReader(ecl).createGlobalCursor().count() - (int) globalBlocks.get();
		Pair<NimbusVersion,AmbStats> nicePair = new Pair<NimbusVersion,AmbStats>(returnMe, staties);
		return nicePair;
	}
	
	public static void repair(JobControl control, int maxRepairEffortMins, DoubleConsumer progress) {
		if(DataStores.instance().count() > 2) {
			throw new CloudException("Repair cannot be run without an ODS configured");
		}
		Ods ds1 = DataStores.instance().getOds(0);
		Ods ds2 = DataStores.instance().getOds(1);
		
		repair(ds1, ds2, maxRepairEffortMins, control, VaultUtil.nestedProgress(progress, 0, 50));
		progress.accept(50);
		control.control();
		repair(ds2, ds1, maxRepairEffortMins, control, VaultUtil.nestedProgress(progress, 0, 50));
		progress.accept(100);
	}
	
	private static void repair(Ods source, Ods dest, int maxRepairEffortMins, JobControl control, DoubleConsumer progress) {
		List<Print> bad = dest.revealBadBlockManager().allBad();
		List<Print> healed = new ArrayList<Print>();
		Stopwatch watchy = new Stopwatch();
		int tots = bad.size();
		int current = 0;
		for(Print p : bad) {
			if(maxRepairEffortMins > 0 && watchy.getElapsed(TimeUnit.MINUTES) > maxRepairEffortMins) {
				break;
			}
			control.control();
			try {
				if(source.getCloudAdapter().stat(p.toString())) {
					sendPrint(p, dest.getCloudAdapter(), new CloudBlockSource(source.getCloudAdapter()), new JobControl());
					healed.add(p);
				}
			} catch(Throwable t) {
				LOGGER.error("Unable to repair block due to error " + t);
			}
			double val = ((double) ++current) / ((double) tots) * 100.00;
			progress.accept(val);
		}
		try {
			VaultUtil.ezExec(CMD_FS_SYNC);
			dest.revealBadBlockManager().heal(healed);
		} catch(IOException ioe) {
			;
		}
		progress.accept(100);
		
		
	}
	
	/**
	 * Bakes in retries on sending a print
	 */
	private static int sendPrint(Print p, CloudAdapter adp, BlockSource blox, JobControl control) {
		Stopwatch timeout = new Stopwatch();
		while(true) {
			byte [] bites;
			try {
				control.control();
				String destPath = adp.getBlockPath(p);
				// Optimized route doesn't use Java memory buffers
				// But s3fs don't like it
				if(destPath != null) {
					// NB this triggers "meter" when needed
					String sourcePath = blox.getBlockPath(p);
					if(sourcePath != null) {
						// Perform filesystem copy
						int sz = (int) new File(sourcePath).length();
						// Meter the upload
						if(adp instanceof BandwidthCloudAdapter) {
							BandwidthCloudAdapter bca = (BandwidthCloudAdapter) adp;
							bca.getMeter().getUploadMeter().meter(sz);
						}
						String rez = VaultUtil.ezExec("cp -f $1 $2", sourcePath, destPath);
						if(rez.trim().isEmpty() == false) {
							throw new CloudException("Block vault (copy) of " + p + " failed with error " + rez);
						}
						return sz;
					}
				}
				// Fall out to here if optimized route not available
				// Default route uses Java memory buffers
				bites = blox.getBlock(p);
				if(bites == null) {
					// NoOpBlockSource returns null for blocks that have already been sent
					if(blox instanceof NoOpBlockSource) {
						return 0;
					} 
					throw new CloudException("Illegal null block detected for print " + p);
				}
				VaultUtil.putBlockFromMem(bites, bites.length, adp, p.toString());
				return bites.length;
			} catch(CloudException|IOException ce) {
				try {
					Thread.sleep(RETRY_SLEEP_MS);
				} catch (InterruptedException ie) {;}
				LOGGER.debug( "Retrying put of block with error", ce);
				if(timeout.getElapsed(TimeUnit.MILLISECONDS)> RETRY_TIMEOUT_MS) {
					if(ce instanceof CloudException) throw (CloudException) ce;
					throw new CloudException(ce);
				}
			}
		}
	}
	
	
	
	
	
	private static void createEcl(JobAmbDesc desc, String eclLoc, DoubleConsumer progress, JobControl control) throws IOException {
		VmVersion version = desc.toVersion();
		
		final EclHolder holder = new EclHolder(eclLoc, version);
		IntStream weewee = IntStream.range(0, desc.hcls.size());
		int threadCount = desc.hcls.size();
		if(threadCount > MAX_ECL_THREADS) {
			threadCount = MAX_ECL_THREADS;
		}
		final List<HclWriterUtil> myHclWriters = new ArrayList<HclWriterUtil>();
		int posy = 0;
		// First pass to add disks in a single thread
		for(String hclPath : desc.hcls) {
			HclReaderUtil reader = new HclReaderUtil(hclPath);
			HclCursor c = reader.createCursor();
			HclWriterUtil writer = holder.addDisk(posy++, c.count());
			myHclWriters.add(writer);
		}
		// Second pass to actually poop out the prints to the ECL writers
		
		final ArrayList<Exception> eArray = new ArrayList<Exception>();
		final AtomicLong manhattan = new AtomicLong();
		
		VaultUtil.reduce(threadCount, () ->
		weewee.parallel().forEach(pos -> {
			try { 
				String s = desc.hcls.get(pos);
				HclReaderUtil reader = new HclReaderUtil(s);
				HclCursor c = reader.createCursor();
				double currentStage = ((double) pos / desc.hcls.size()) * 100.00; 
				double step  = 100.00 / ((double) desc.hcls.size());
				DoubleConsumer prog2 = VaultUtil.nestedProgress(progress, currentStage, step);
				HclWriterUtil writer = myHclWriters.get(pos);
				while(c.hasNext()) {
					writer.writePrint(c.next());
					double myProg = ((double) c.getPosition() / c.count()) * 100.00;
					prog2.accept(myProg);
					control.control();
					manhattan.incrementAndGet();
				}
				writer.flush();
				prog2.accept(100);
			} catch(Exception e) {
				eArray.add(e);
			}
		}));
		
		if(eArray.size() > 0) {
			throw new CloudException(eArray.get(0));
		}
		long totsCount = holder.getDisks().stream().mapToLong(d -> d.getHclCount()).sum();
		if(manhattan.get() != totsCount) {
			throw new CloudException("Creation of ECL failed; print count doesn't match those of all individual disks, " 
						+ totsCount + " orig vs " + manhattan.get() + " created erroneously?");
		}
		// Write coda only to the last of the herd
		myHclWriters.get(myHclWriters.size() -1).writeCoda();
		progress.accept(100);
	}
	
	private static void updateNimbusStats(NimbusVersion v, AmbStats stat) {
		if(stat == null) {
			throw new CloudException("Unable to update stats due to likely commit timeout!");
		}
		
		for(int diskNum = 0; diskNum < v.getDiskSizes().size(); ++diskNum) {
			HeaderManager.HeaderEvent h1 = new HeaderManager.HeaderEvent();
			HeaderManager.HeaderEvent h2 = new HeaderManager.HeaderEvent();
			HeaderManager.HeaderEvent h3 = new HeaderManager.HeaderEvent();
			
			h1.changeSize = stat.bytesSent[diskNum] + stat.bytesSkipped[diskNum];
			h1.type = HeaderManager.HeaderEventType.updatePreDedupSize;
			h1.version = v;
			h1.diskNum = diskNum;
			
			h2.changeSize = stat.bytesSent[diskNum];
			h2.type = HeaderManager.HeaderEventType.updatePostDedupSize;
			h2.version = v;
			h2.diskNum = diskNum;
			
			h3.changeSize = v.getDiskSizes().get(diskNum);
			h3.type = HeaderManager.HeaderEventType.updateFullSize;
			h3.version = v;
			h3.diskNum = diskNum;
			
			NimbusDbListener.instance().accept(h1);
			NimbusDbListener.instance().accept(h2);
			NimbusDbListener.instance().accept(h3);
			LOGGER.info("Updating stats for vm version " + v.getVmId() + " disk " + diskNum + " and job " + v.getJobId() 
				+ ": pre-dedup size is: " + h1.changeSize
				+ " and full size is: " + h3.changeSize);
		}
		// Force all pending nimbusDB updates to kick out
		NimbusDbListener.instance().flush();
		
	}
	
	private static void updateJobStats(long jobId, AmbStats stat) {
		HeaderManager.HeaderEvent h1 = new HeaderManager.HeaderEvent();
		HeaderManager.HeaderEvent h2 = new HeaderManager.HeaderEvent();
		h1.type = HeaderManager.HeaderEventType.updateJobOrig;
		h2.type = HeaderManager.HeaderEventType.updateJobDelta;
		h2.changeSize = stat.bytesSent[0];
		h1.changeSize = stat.blocksSkipped[0] + h2.changeSize;
		
		h1.diskNum  = (int) jobId;
		h2.diskNum = (int) jobId;		
		NimbusDbListener.instance().accept(h1);
		NimbusDbListener.instance().accept(h2);
		
		NimbusDbListener.instance().flush();
		LOGGER.info("Updating vault stats for vault job " + jobId 
				+ ": orig size is: " + h1.changeSize
				+ " and sent size is: " + h2.changeSize);
	}
	
		
}
	
/****************************************************************************************
 * 
 * Begin support classes
 *
 ****************************************************************************************/





/**
 * A helper class to load information about AMBs, HCLs, and meta from the job directory
 *
 */
class JobAmbDesc {
	long jobId;
	long vmId;
	// Maps device to AMB path
	//List<Pair<Integer, String>> ambs = new ArrayList<Pair<Integer, String>>();
	List<StupidMeta> metas;
	String vmMetaInfo;
	String vmMetaBlob;
	List<String> hcls;
	
	/**
	 * Constructs the JobAmbDesc from a preexising resume file by searching the validate dir for AMBs
	 */
	public JobAmbDesc(ResumeFile resumeFile) {
		jobId = resumeFile.getJobId();
		vmId = resumeFile.getVmId();
		
	}
	
	
	/**
	 * Constructs the JobAmbDesc from a job directory source as returned by a VisorTool job
	 */
	JobAmbDesc(String source) throws IOException {
		//source = VaultSettings.instance().cleanWinePath(source);
		File myDir = new File(source);
		// it goes job/vm/device
		File parentDir = myDir.getParentFile();
		if(parentDir == null) {
			throw new CloudException("Invalid job path: " + source);
		}
		try {
			jobId = Long.parseLong(parentDir.getName());
		} catch(Throwable e) { 
			throw new CloudException("Unable to determine job directory for path " + parentDir.getPath());
		}
		try {
			vmId = Long.parseLong(myDir.getName());
		} catch(Throwable e) { 
			throw new CloudException("Unable to determine VM directory for path " + myDir.getPath());
		}
		
		vmMetaInfo = source + File.separator + "vmmeta.info";
		vmMetaBlob = source + File.separator + "vmmeta.blob";
		String diskNumFile = source + File.separator + "dev.num";
		if(new File(vmMetaInfo).exists() == false) {
			throw new CloudException("Missing file " + vmMetaInfo);
		}
		if(new File(vmMetaBlob).exists() == false) {
			throw new CloudException("Missing file " + vmMetaBlob);
		}
		if(new File(diskNumFile).exists() == false) {
			throw new CloudException("Missing file " + diskNumFile);
		}
		
		String diskNumText = new String(Files.readAllBytes(Paths.get(diskNumFile)), StandardCharsets.UTF_8);
		int diskNum = Integer.parseInt(diskNumText);
		
		// Preallocate HCLs and metas so they can be indexed by offset
		hcls = new ArrayList<String>();
		metas = new ArrayList<StupidMeta>();
		
		List<String> hclTemp = VaultUtil.listFiles(myDir.getPath(), "*.hcl");
		if(hclTemp == null || hclTemp.isEmpty()) {
			throw new CloudException("No HCL found at path " + myDir.getPath());
		}
		for(String fName : hclTemp) {
			int pos = getDevicePosFromFileName(fName);
			while(pos >= hcls.size()) {
				hcls.add(null);
			}
			hcls.set(pos, fName);
		}
		// Now do metas
		List<String> metaTemp = VaultUtil.listFiles(myDir.getPath(), "[!A-z]*.meta");
		if(metaTemp == null || metaTemp.isEmpty()) {
			throw new CloudException("No meta file found at path " + myDir.getPath());
		}
		for(String fName : metaTemp) {
			int pos = getDevicePosFromFileName(fName);
			while(pos >= metas.size()) {
				metas.add(null);
			}
			metas.set(pos, new StupidMeta(fName));
		}
		
		// Some sanity checking
		if(metaTemp.size() != diskNum) {
			throw new CloudException("Munge returned a dev.num of " + diskNum + " but we have " + metaTemp.size() + " meta files");
		}
		if(hclTemp.size() != diskNum) {
			throw new CloudException("Munger returned a dev.num of " + diskNum + " but we have " + hclTemp.size() + " HCL files");
		}
		
	}

	/**
	 * Used only for imports
	 * @return
	 */
	List<String> getRlogs() {
		ArrayList<String> rlogs = new ArrayList<String>();
		for(String s : hcls) {
			String cool = s.substring(0, s.lastIndexOf('.')) + ".rlog";
			rlogs.add(cool);
		}
		return rlogs;
	}
	
	VmVersion toVersion() throws IOException {
		VmVersion version = new VmVersion();
		try (BufferedReader br = new BufferedReader(new FileReader(vmMetaInfo))) {
			try {
				String line = br.readLine();
				if(line == null) {
					throw new CloudException(vmMetaInfo + " is not well-formed--missing uuid");
				}
				version.setPlatformStyleUuid(line);
				line = br.readLine();
				if(line == null) {
					throw new CloudException(vmMetaInfo + " is not well-formed--missing virt type");
				}
				version.setVirtualType(Integer.parseInt(line));
				line = br.readLine();
				if(line == null) {
					throw new CloudException(vmMetaInfo + " is not well-formed--missing timestamp");
				}
				version.setVersion(Long.parseLong(line));
				line = br.readLine();
				if(line == null) {
					throw new CloudException(vmMetaInfo + " is not well-formed--missing VM name");
				}
				version.setVmName(line);
			} catch(NumberFormatException nfe) {
				throw new CloudException(vmMetaInfo + " is not well-formed");
			}
		}
		// Deal with meta binary garbagio
		int len = (int) new File(vmMetaBlob).length();
		if(len > VaultCommandAdapter.MAX_META_FILE_SIZE) {
			throw new CloudException(vmMetaBlob + " is unreasonably large");
		}
		byte [] chunky = new byte[len];
		try (FileInputStream fis = new FileInputStream(vmMetaBlob)) {
			if(VaultUtil.ezLoad(fis, chunky, len) == false) {
				throw new CloudException("Unexpected EOF");
			}
		}
		version.setMetaData(new String(chunky, "US-ASCII"));
		// Assemble disk sizes
		List<Long> diskSizes = new ArrayList<Long>();
		for(StupidMeta stupid : metas) {
			if(stupid == null) {
				throw new CloudException("Missing required disk meta file " + diskSizes.size() +1 + ".meta");
			}
			diskSizes.add(stupid.devBytes);
		}
		version.setDiskSizes(diskSizes);
		return version;
		
	}
	
	private int getDevicePosFromFileName(String fName) {
		try {
			String end = new File(fName).getName();
			String posStr = end.split("\\.")[0];
			return Integer.parseInt(posStr);
		} catch(Exception e) {
			throw new CloudException(fName + " does not follow deviceNum.ext naming convention");
		}
	}
}

/**
 * Represents a meta file
 *
 */
class StupidMeta {
	String hash;
	long devBytes;
	long storedBytes;
	long changedBytes;
	
	public StupidMeta() {
		;
	}
	
	public StupidMeta(String path) throws IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(path))) {
			String theLine = br.readLine();
			String [] awesome = theLine.split(",");
			if(awesome == null || awesome.length < 4) {
				throw new CloudException(path + " does not contain well-formed meta file");
			}
			try {
				hash = awesome[0];
				devBytes = Long.parseLong(awesome[1]);
				storedBytes = Long.parseLong(awesome[2]);
				changedBytes = Long.parseLong(awesome[3]);
			} catch(Throwable e) {
				throw new CloudException(path + " is not a well-formed meta file");
			}
		}
	}
}
