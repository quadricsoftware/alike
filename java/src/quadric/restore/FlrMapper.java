package quadric.restore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;


/**
 * Maps virtual img files backed by FUSE to mount points using fdisk to determine partitions and mount to mount 'em up
 * 
 * Also uses an LRU to prevent too many mounts from being created, so the oldest are unmounted when greater than MAX_FLR_MOUNTS
 *
 */
public class FlrMapper {
	private static final Logger LOGGER = LoggerFactory.getLogger( FlrMapper.class.getName() );
	
	private int maxMounts;
	private String flrMountBase;
	private String restoreMountBase;
	
	private Set<Mount> hardMounts = new HashSet<Mount>(); 
	private Map<EclCacheKey,Mount> softMountedDrives = new HashMap<EclCacheKey,Mount>();
	private AtomicInteger softMountCount = new AtomicInteger();
	volatile boolean isShutdown = false;
	private int mountTimeoutMins;
	ExecutorService executor = Executors.newSingleThreadExecutor();
	private static FlrMapper me = new FlrMapper();
	
	
	
	public static FlrMapper instance() {
		return me;
	}
	
	private FlrMapper() {
		;
	}
	
	public void init() {
		maxMounts = VaultSettings.instance().getMaxFlrMounts();
		flrMountBase = VaultSettings.instance().getFlrMountBase();
		restoreMountBase = VaultSettings.instance().getRestoreMountBase();
		mountTimeoutMins = VaultSettings.instance().getFlrTimeoutMins();
		LOGGER.info("FLR mapping will use path " + restoreMountBase + " with a maximum hard mount count of " + maxMounts);
		// Cleanup
		File makeMe = new File(flrMountBase);
		if(makeMe.exists() == false) {
			throw new CloudException(flrMountBase + " must exist");
		}
		
		
		// 	Make sure to undo this when we're done
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdown();
			
		}));
		
		Thread t = new Thread(() -> {
			while(isShutdown == false) {
				expungeLeastRecentMounts(false);
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) { ; }
			}
		});
		t.start();
	}
	
	public void shutdown() {
		synchronized(this) {
			isShutdown = true;
			if(softMountedDrives.size() > 0) {
				LOGGER.info("GRACEFUL SHUTDOWN. Tearing " + softMountedDrives.size() + " FLR mounts down now");
			}
			for(Mount m : softMountedDrives.values()) {
				try {
					//LOGGER.debug("Unmounting " + m.mntPoint);
					m.hardUnmount();
					m.softUnmount();
				} catch(Throwable t) { 
					LOGGER.error("Unable to unmount mount at " + m.mntPoint, t);
				}
			}
		}
		
		if(softMountedDrives.size() > 0) {
			LOGGER.info("FINISHED FLR dismount");
		}
	}
	
	public boolean isHardMounted(EclCacheKey key) {
		if(isShutdown) {
			return false;
		}
		Mount m = null;
		synchronized(this) {
			m = softMountedDrives.get(key);
			if(m == null) {
				return false;
			}
		}
		return true;
	}
	
	public void updateAccessTime(VmVersionHierarchy symPath, EclCacheKey key) {
		if(isShutdown) {
			return;
		}
		boolean mustHardMount = false;
		Mount m = null;
		synchronized(this) {
			m = softMountedDrives.get(key);
			if(m == null) {
				getMountedPath(symPath, key);
				m = softMountedDrives.get(key);
			}
			if(hardMounts.contains(m) == false) {
				hardMounts.add(m);
				mustHardMount = true;
			}
		}
		final Mount m2 = m;
		m.accessTime = System.currentTimeMillis();
		// Need to background this 
		// since it execs to the filesystem and calls back into restorefs
		if(mustHardMount) {
			executor.submit(() -> {
				m2.hardMountIfNeeded();
				expireHardMounts();
			});
		}
		
	}

	
	public String getMountedPath(VmVersionHierarchy symPath, EclCacheKey key) {
		if(isShutdown) {
			return null;
		}
		//Stopwatch watchy = new Stopwatch();
		Mount m;
		synchronized(this) {
			m = softMountedDrives.get(key);
		
			if(m == null) {
				String sourceFile = restoreMountBase + symPath.getParent() + "/" + key.getDiskNum() + ".rnd";
				String mountPoint = flrMountBase + "/" + softMountCount.getAndIncrement();
				m = new Mount(sourceFile, mountPoint);
				LOGGER.info("FLR is soft-mounting " + sourceFile + " at " + mountPoint);
				softMountedDrives.put(key, m);
			} else {
				LOGGER.trace("Mount point was cached...");
			}
		}	
		//LOGGER.debug("getMountedPath for  " + symPath.toString() + " returned in " + watchy.getElapsed(TimeUnit.MILLISECONDS)); 
		return m.mntPoint;
	}	
	
	void expungeLeastRecentMounts(boolean forceOne) {
		Mount oldest = null;
		List<Mount> staleMounts = new ArrayList<Mount>();
		synchronized(this) {
			long now = System.currentTimeMillis();
			for(Mount m : hardMounts) {
				if((now - m.accessTime) > (mountTimeoutMins * 60 * 1000)) {
					LOGGER.debug("Mount " + m.accessTime + " is stale");
					staleMounts.add(m);
				} else if(oldest == null || m.accessTime < oldest.accessTime) {
					oldest = m;
					LOGGER.trace("Setting oldest to " + m);
				}
			}
		}
		if(forceOne && staleMounts.isEmpty() == true) {
			if(oldest != null) {
				staleMounts.add(oldest);
			}
		}
		for(Mount m : staleMounts) {
			try {
				m.hardUnmount();
			} catch(Throwable t) {
				LOGGER.error("Error unmounting FLR mount for " + m.imgFile, t);
			}
		}
		if(staleMounts.isEmpty() == false) {
			synchronized(this) {
				hardMounts.removeAll(staleMounts);
				LOGGER.debug("Freed " + staleMounts.size() + " hard mounts, current count is " + hardMounts.size());
			}
		}
	}
	
	void expireHardMounts() {
		boolean needsToExpire = false;
		while(true) {
			synchronized(this) {
				if(hardMounts.size() >= maxMounts) {
					LOGGER.debug("Max mounts of " + maxMounts + " reached, one hard mount will be dropped");
					needsToExpire = true;
				} else {
					break;
				}
			}
			// Don't do this with a lock on, as it will cause deadlock
			if(needsToExpire) {
				LOGGER.trace("Max FLR hard mount count exceeded, expunging at least one FLR mount");
				expungeLeastRecentMounts(true);
			}
		}
	}
	
}

/**
 * Represents an FLR mount point for a single drive
 *
 */
class Mount {
	private static final Logger LOGGER = LoggerFactory.getLogger( Mount.class.getName() );
	private static final String GDISK_COMMAND = "/sbin/gdisk -l $1";
	private static final String MOUNT_COMMAND_GENERIC = "sudo mount -o ro $1 $2";
	private static final String MOUNT_COMMAND_WHOLE_DISK = "sudo mount -o loop,ro,norecovery,offset=0 $1 $2";
	private static final String MOUNT_COMMAND_EXT4 = "sudo mount -t ext4 -o noload,ro $1 $2";
	private static final String MOUNT_COMMAND_LVM = "sudo mount $1 $2";
	private static final String MOUNT_COMMAND_NTFS = "sudo mount -t ntfs3 -o umask=000,ro $1 $2";
	//private static final String MOUNT_COMMAND_NTFS_G_MONEY = "sudo mount -t ntfs-3g -o umask=000,ro $1 $2";
	private static final String MOUNT_COMMAND_NTFS_G_MONEY_WITH_OFFSET = "sudo mount -t ntfs3 -o umask=000,ro,offset=$1 $2 $3";
	//private static final String UMOUNT_COMMAND = "sudo umount $1";
	private static final String UMOUNT_LAZY_COMMAND = "sudo umount -l $1";
	private static final String KPARTX_ADD_COMMAND = "sudo privTool kpartx -av $1";
	private static final String KPARTX_DEL_COMMAND = "sudo privTool kpartx -d $1";
	private static final String VGCHANGE_COMMAND = "sudo vgchange -a y $1";
	private static final String KPARTX_REGEX = 				"add map (\\w+)"; 
	private static final String SECTOR_REGEX = 				"sector size:\\W(\\d+)";
	private static final String FILE_SYSTEM_ID_REGEX = 		"\\s+\\d+\\s+(\\d+)\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\w+)";
	private static final String LVM_VOLUME_GROUP_REGEX = 	"group\\s\"(\\S+)\"";	
	private static final String [] WINDOWS_PARTITION_TYPES = {"8700", "700", "0700" };
	private static final String MICROSOFT_RESERVED_PARTITION_TYPE = "0C01";
	private static final String PARTITION_TYPE_WHOLE_DISK = "__A3_WHOLEDISK";
	private static final String LVM_PARTITION_TYPE = "8e00";
	private static final String EXT_PARTITION_TYPE = "8300";
	private static final String KPARTX_BAD_LOOP_PATH = "DEAD_LOOP";
	private static final int KPARTX_LOOP_TIMEOUT_SECS = 30;
	
	
	
	String imgFile;
	String mntPoint;
	List<PartInfo> meta = null;
	private boolean isHardMounted = false;
	private boolean isTransitioning = false;
	boolean hasLvm = false;
	String restoreMountBase;
	volatile long accessTime;
	
	Mount(String absSourceFile, String dest) {
		restoreMountBase = VaultSettings.instance().getRestoreMountBase();
		imgFile = absSourceFile;
		mntPoint = dest;
		
	}

	
	/**
	 * Associates the mount point, if needed
	 * 
	 */
	void hardMountIfNeeded() {
		synchronized(this) {
			accessTime = System.currentTimeMillis();
			if(isTransitioning || isHardMounted || FlrMapper.instance().isShutdown) {
				return;
			}
			LOGGER.debug("FLR Must hard-mount " + mntPoint);
			// Background it, or else it will deadlock
			isTransitioning = true;

		}
		doHardMounting();
		
		
	}
	
	void doHardMounting() {
		int mountedOkCount = 0;
		try {
			meta = createLoopAndGetPartitionMeta();
			
			VaultUtil.createDirIfNeeded(mntPoint);
			for(int x = 0; x < meta.size(); ++x) {
				String child = mntPoint + "/p" + mountedOkCount;
				VaultUtil.createDirIfNeeded(child);
				PartInfo p = meta.get(x);
				if(mountPartition(child, p)) {
					mountedOkCount++;
				}
			}
			createParsableSymLink();
			
		} catch(Throwable ioe) {
			LOGGER.error("unable to mount FLR due to errors", ioe);
		} finally {
			synchronized(this) {
				if(mountedOkCount > 0) {
					LOGGER.info("Successfully mounted " + mountedOkCount + " partitions of image " + imgFile);
				}
				isTransitioning = false;
				isHardMounted = true;
				accessTime = System.currentTimeMillis();
			}
		}
		
	}

	
	/**
	 * Some FLR clients need to remap permissions to the FLR directory by passing through FUSE.
	 * So we will furnish FUSE a symlink to the FLR mount so it doesn't need to know about "mountCount" and how 
	 * that int associates to mounts.
	 * 
	 *  The symlink will be site_vault_version_disk.
	 */
	private void createParsableSymLink() {
		VaultUtil.createDirIfNeeded(mntPoint);
		String archPath = imgFile.substring(restoreMountBase.length());
		VmVersionHierarchy arch = new VmVersionHierarchy(archPath);
		String symLink = arch.getSymLinkPathForFlr();
		try {
			String rez = VaultUtil.ezExec("ln -sf $1 $2", mntPoint, symLink);
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
	}
	
	synchronized void softUnmount() {
		// Remove mount point
		new File(mntPoint).delete();
		
	}
	
	void hardUnmount()  {
		if(isHardMounted == false) {
			return;
		}
		synchronized(this)  {
			isTransitioning = true;
			isHardMounted = false;
		}
		try {
			LOGGER.info("Unmounting system devices for FLR mount " + mntPoint);
			// Remove symlink
			String archPath = imgFile.substring(restoreMountBase.length());
			new File(new VmVersionHierarchy(archPath).getSymLinkPathForFlr()).delete();
			// Find all mounted partitions to toast
			File [] filez = new File(mntPoint).listFiles();
			Throwable ex = null;
			for(File f : filez) {
				if(f.isDirectory()) {
					try {
						unmountPartition(f.getPath());
					} catch(Throwable t) {
						ex = t;
					}
				}
			}
			if(ex != null) {
				throw new CloudException(ex);
			}
			// Kill the kpartx
			try {
				for(PartInfo p : meta) {
					if(p.type.equals(PARTITION_TYPE_WHOLE_DISK)) {
						// No need to do anything here, as unmount SHOULD release appropriate resources...
						continue;
					}
					String mapper = "/dev/mapper/loop";
					String device  = "/dev/loop" + p.loop.substring(mapper.length()).split("p")[0];
					LOGGER.debug("Issuing kpartx -d for " + device);
					String output = VaultUtil.ezExec(KPARTX_DEL_COMMAND, device);
					if(output.length() > 0) {
						LOGGER.debug(output);
					}
					LOGGER.debug("Issuing losetup -d " + device);
					output = VaultUtil.ezExec("sudo /sbin/losetup -d $1", device);
					if(output.length() > 0) {
						LOGGER.debug(output);
					}
				}
				/*if(output.length() > 0) {
					throw new CloudException("kpartx unmount errored with " + output);
				}*/
			} catch(IOException ioe) {
				throw new CloudException(ioe);
			}
			if(hasLvm) {
				// Need to clean this up too
				unmountLvm(imgFile);
			}
			LOGGER.debug("Successfully unmounted image " + imgFile);
		} finally {
			synchronized(this) {
				isTransitioning = false;
			}
		}
	}
	
	void unmountPartition(String path) throws IOException {
		
		String output = VaultUtil.ezExec(UMOUNT_LAZY_COMMAND, path);
		
		if(output.length() > 0) {
			LOGGER.error("Lazy unmount errored with: " + output);
		}
		// Clean up the mount point
		boolean delOk = new File(path).delete();
		if(delOk == false) {
			throw new CloudException("Unable to delete directory " + path);
		}
	}
	
	
	
	List<PartInfo> createLoopAndGetPartitionMeta() throws IOException {
		List<PartInfo> myMetas = new ArrayList<PartInfo>();
		String output = VaultUtil.ezExec(GDISK_COMMAND, imgFile);
		LOGGER.debug(output);
		Matcher lamewad = Pattern.compile(SECTOR_REGEX).matcher(output);
		long sectorSize = 0;
		if(lamewad.find() == true) {
			String sectorSizeString = lamewad.group(1);
			sectorSize = Long.parseLong(sectorSizeString);
		}
		
		Matcher m = Pattern.compile(FILE_SYSTEM_ID_REGEX).matcher(output);
		boolean foundAnything = false;
		for(int x = 0; m.find(); ++x) {
			foundAnything = true;
			LOGGER.debug("Found partition type of " + m.group(2) + " with sector offset " + m.group(1));
			PartInfo p = new PartInfo();
			p.type = m.group(2);
			try {
				p.offset = Long.parseLong(m.group(1));
				p.offset *= sectorSize;
			} catch(Throwable t) { ;}			
			myMetas.add(p);
			
		}
		if(myMetas.isEmpty()) {
			LOGGER.info("No partitions detected on disk " + imgFile 
						+ " , will try to mount ENTIRE disk generically");
			PartInfo p = new PartInfo();
			p.offset = 0;
			p.type = PARTITION_TYPE_WHOLE_DISK;
			// Use the ENTIRE file as the "loop", which will be generated automatically by the 
			// mount -o loop command for this type 
			p.loop = imgFile;
			myMetas.add(p);
			return myMetas;
		}
		if(foundAnything == false) {
			LOGGER.info("Cannot find partition type matches against " + output);
		}
		
		// Now run kpartx
		output = VaultUtil.ezExec(KPARTX_ADD_COMMAND, imgFile);
		LOGGER.trace(output);
		m = Pattern.compile(KPARTX_REGEX).matcher(output);
		int partNo = 0;
		while(m.find()) {
			String awesome = m.group(1);
			LOGGER.trace("Found kpartx partition " + awesome);
			String loopPath = "/dev/mapper/" + awesome;
			Stopwatch watchy = new Stopwatch();
			while(true) {
				if(new File(loopPath).exists()) {
					break;
				}
				try {Thread.sleep(50); } catch (InterruptedException e) {;}
				if(watchy.getElapsed(TimeUnit.SECONDS) > KPARTX_LOOP_TIMEOUT_SECS) {
					LOGGER.error("Waited for kpartx loop mapper but it did not appear at " + loopPath);
					loopPath = KPARTX_BAD_LOOP_PATH;
					break;
				}
			}
			PartInfo farty = null;
			try {
				farty = myMetas.get(partNo++);
			} catch(IndexOutOfBoundsException boundse) { ;}
			if(farty == null) {
				farty = new PartInfo();
				myMetas.add(farty);
			}
			farty.loop = loopPath;
		}
		return myMetas;
	}
	
	boolean mountPartition(String path, PartInfo info) {
		try {
			// Not sure why this happens, but whatev
			if(info.loop == null) {
				//LOGGER.debug("Skipping loopless partition info at " + path);
				return false;
			}
			// Skip failed loops
			if(info.loop.equals(KPARTX_BAD_LOOP_PATH)) {
				return false;
			}
			if(info.type.equals(MICROSOFT_RESERVED_PARTITION_TYPE)) {
				LOGGER.trace("Skipping MS system reserved partition...");
				return false;
			}
			//boolean isLvm = false;
			if(info.type.equals(LVM_PARTITION_TYPE)) {
				LOGGER.debug( "*****LVM detected...");
				return mountLvm(path, info);
			}
			
			String cmd  = MOUNT_COMMAND_GENERIC;
			if(info.type.equals(PARTITION_TYPE_WHOLE_DISK)) {
				cmd = MOUNT_COMMAND_WHOLE_DISK;
			} else if(Arrays.asList(WINDOWS_PARTITION_TYPES).stream().anyMatch(t -> t.equals(info.type))) {
				cmd = MOUNT_COMMAND_NTFS;
				//cmd  = MOUNT_COMMAND_NTFS_G_MONEY;
				LOGGER.trace("******DETECTED NTFS...");
			} else if(info.type.equals(EXT_PARTITION_TYPE)) {
				LOGGER.trace("*****DETECTED EXT?");
				cmd = MOUNT_COMMAND_EXT4;
			}
			LOGGER.debug("About to mount img/loop: " + info.loop + " to path: " + path + " with command: " + cmd);
			String output = VaultUtil.ezExec(cmd, "" + info.loop, path);
			if(output.contains("bad superblock")) {
				// We can't mount this sucker?
				LOGGER.info("Unable to mount partition at loop " + info.loop + " for image " + imgFile);
				new File(path).delete();
				return false;
			} else if(output.contains("unknown filesystem type 'swap'")) {
				LOGGER.trace("Skipping swap partition");
				new File(path).delete();
				return false;
			} else if(output.trim().length() > 0) {
				if(output.contains("partition is smaller")) {
					String masterLooper = info.loop.substring(0, info.loop.lastIndexOf('p'));
					masterLooper = "/dev/" + new File(masterLooper).getName();
					LOGGER.error("Partition table likely too SMALL for partition--will try mounting from parent device");
					output = VaultUtil.ezExec(MOUNT_COMMAND_NTFS_G_MONEY_WITH_OFFSET, "" + info.offset, masterLooper, path);
					if(output.trim().length() > 0) {
						LOGGER.debug(output);
						new File(path).delete();
						return false;
					}
					return true;
				}
				LOGGER.info("Skipping partition for img " + imgFile + " at loop " + info.loop + " with mount error: " + output);
				new File(path).delete();
				return false;
			} 
			LOGGER.trace("Mounted FLR partition at " + path);
			return true;
		} catch(IOException ioe) {
			LOGGER.error("Problem mounting " + path, ioe);
		}
		return false;
	}
	
	boolean mountLvm(String path, PartInfo info) throws IOException {
		LOGGER.info("LVM not currently supported");
		return false;
		/*String output = VaultUtil.ezExec(KPARTX_ADD_COMMAND, imgFile);
		output = VaultUtil.ezExec("sudo pvs");
		output = VaultUtil.ezExec("sudo vgscan");
		
		// Regex the output
		Matcher lamewad = Pattern.compile(LVM_VOLUME_GROUP_REGEX).matcher(output);
		boolean hasFoundAtLeastOne = false;
		while(lamewad.find() != false) {
			hasFoundAtLeastOne = true;
			String volName = lamewad.group(1);
			output = VaultUtil.ezExec(VGCHANGE_COMMAND, volName);
			// TODO: determine where the VGCHANGE put this SOB
			String child = mntPoint + "/p" + mountCount++;
			output = VaultUtil.ezExec(MOUNT_COMMAND_LVM, output, child);
		}*/
		
		
	}
	 
	void unmountLvm(String imgFile) {
		/* try {
			String output = VaultUtil.ezExec(KPARTX_DEL_COMMAND, imgFile);
			if(output.trim().length() > 0) {	
				throw new CloudException(output);
			}
			LOGGER.debug( "Unmounted LVM from " + imgFile);
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		} */
	}

	
	
	
	
}

class PartInfo {
	long offset;
	String loop = "__BAD";
	String type = "__UNKNOWN";
	
	public String toString() {
		return "PartInfo Offset: " + offset + " Loop: " + loop + " type: " + type;
	}
}
