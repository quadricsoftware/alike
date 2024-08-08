package quadric.ods;


import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import quadric.blockvaulter.CloudException;
import quadric.util.HclCursor;
import quadric.util.HclReaderUtil;
import quadric.util.VaultUtil;

/**
 * 
 *
 */
public class EclReader {
	private static int MAX_METADATA_SIZE = 1024 * 1024;
	private static int MAX_VM_NAME_SIZE = 1024 * 10;
	
	EclHeader header = new EclHeader();
	List<EclDisk> disks = new ArrayList<EclDisk>();
	String vmName;
	String meta;
	String path;
	int cacheSize;
	
	public EclReader(String path, int cacheSize) {
		this.path = path;
		try (FileChannel fc = FileChannel.open(Paths.get(path))) {
			init(fc, cacheSize);
		} catch(IOException ioe){ 
			throw new CloudException(ioe);
		}
	}
	
	public EclReader(String path) {
		// Use the reader's default val
		this(path, HclReaderUtil.PRINT_PREFETCH_COUNT);
		
	}
	
	public List<EclDisk> getDisks() {
		return disks;
	}
	
	public EclHeader getHeader() {
		return header;
		
	}
	
	public VmVersion toVmVersion(EclFlags flag) {
		VmVersion vmv = new VmVersion();
		vmv.setVmName(vmName);
		vmv.setMetaData(meta);
		vmv.setUuid(header.uuid.toHex());
		vmv.setSiteId(flag.getSiteId());
		vmv.setVaultId(flag.getTxNo());
		vmv.setVersion(header.versionEpoch);
		vmv.setVirtualType(header.virtualType);
		List<Long> diskSizes = new ArrayList<Long>();
		disks.forEach(d -> diskSizes.add(d.getDiskSize()));
		vmv.setDiskSizes(diskSizes);
		return vmv;
		
	}
	
	public HclCursor createCursor(int diskPos) {
		return createCursor(diskPos, 0);
	}
	
	public HclCursor createCursor(int diskPos, int blockIntoDisk) {
		HclReaderUtil reader = new HclReaderUtil(path, header.hclRegionOffset, cacheSize);
		int hclOffset = disks.subList(0, diskPos).stream().mapToInt(e -> e.getHclCount()).sum();
		hclOffset += blockIntoDisk;
		int maxBlock = disks.get(diskPos).getHclCount();
		maxBlock -= blockIntoDisk;
		return reader.createCursor(hclOffset, maxBlock);
	}
	
	public HclCursor createGlobalCursor() {
		HclReaderUtil reader = new HclReaderUtil(path, header.hclRegionOffset, cacheSize);
		return reader.createCursor();
	}
	
	public boolean alreadyHasMd5() {
		HclReaderUtil reader = new HclReaderUtil(path, header.hclRegionOffset, cacheSize);
		return reader.alreadyHasMd5();
	}
	
	private void init(FileChannel fc, int cacheSize) throws IOException {
		this.cacheSize = cacheSize;
		header.load(VaultUtil.ezLoad(fc, header.recordSize()));
		for(int x = 0; x < header.diskCount; ++x) {
			disks.add(new EclDisk());
		}
		if(header.vmNameSize > MAX_VM_NAME_SIZE) {
			throw new CloudException("EclReader found VM name size that is inordinately large");
		}
		if(header.vmNameSize < 1) {
			throw new CloudException("EclReader found VM name size that is less than 1");
		}
		byte [] bites = VaultUtil.ezLoad(fc, header.vmNameSize);
		vmName = new String(bites, "US-ASCII");
		for(EclDisk d : disks) {
			byte [] vile = VaultUtil.ezLoad(fc, d.recordSize());
			d.load(vile);
		}
		// Read meta
		if(header.metadataSize > MAX_METADATA_SIZE) {
			throw new CloudException("EclReader Metadata region is too large");
		}
		bites = VaultUtil.ezLoad(fc, header.metadataSize);
		meta = new String(bites, "US-ASCII");
	}

}
