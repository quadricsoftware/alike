package quadric.ods;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import quadric.blockvaulter.CloudException;
import quadric.util.HclReaderUtil;
import quadric.util.HclWriterUtil;
import quadric.util.Print;
import quadric.util.VaultUtil;
import quadric.vhdx.Guid;

public class EclHolder {
	private List<EclDisk> disks = new ArrayList<EclDisk>();
	private String path;
	private EclHeader header;
	
	public EclHolder(String path, VmVersion v) {
		this.path = path;
		header = new EclHeader();
		header.diskCount = v.getDiskSizes().size();
		header.metadataSize = v.getMetaData().length();
		header.vmNameSize = v.getVmName().length();
		header.uuid = new Guid(v.getNormalizedUuid()); 
		header.versionEpoch = v.getVersion();
		header.virtualType = v.getVirtualType();
		for(Long l : v.diskSizes) {
			EclDisk disk = new EclDisk();
			disk.setDiskSize(l);
			disks.add(disk);
		}
		
		int diskSkipAmt = (new EclDisk().recordSize()) * disks.size();
		header.metadataSize = v.getMetaData().length();
		header.vmNameSize = v.getVmName().length();
		// Determine the region offset
		header.hclRegionOffset = header.recordSize() + diskSkipAmt  + header.metadataSize + header.vmNameSize;
		try (FileOutputStream fis = new FileOutputStream(path, false)) {
			FileChannel ch = fis.getChannel();
			VaultUtil.ezStore(ch, header.store());
			VaultUtil.ezStore(ch, v.getVmName().getBytes("US-ASCII"));
			// Skip the disks for now
			ch.position(header.hclRegionOffset - header.metadataSize);
			VaultUtil.ezStore(ch, v.getMetaData().getBytes("US-ASCII"));
			
		} catch(IOException ieo) { 
			throw new CloudException(ieo);
		}
		
	}
	
	public HclWriterUtil addDisk(int diskPos, int printCount) {
		
		int skipPrints = 0;
	
		for(int x = 0; x < diskPos; ++x) {
			skipPrints += disks.get(x).getHclCount();
			
		}
		EclDisk disk = disks.get(diskPos);
		disk.setHclCount(printCount);
		// Write out the disk metadata--it comes directly after the vm name
		int offset = header.recordSize() + header.vmNameSize;
		offset += diskPos * disk.recordSize();
		try (FileChannel vile = FileChannel.open(Paths.get(path), StandardOpenOption.WRITE)) {
			vile.position(offset);
			VaultUtil.ezStore(vile, disk.store());
		} catch(IOException ieo) { 
			throw new CloudException(ieo);
		}
		// Obtain a writer 
		long skipAmt = header.hclRegionOffset + (skipPrints * HclReaderUtil.RECORD_SIZE);
		HclWriterUtil util = new HclWriterUtil(path, skipAmt);
		return util;
	}
	
	public List<EclDisk> getDisks() {
		return disks;
	}
	
	

}
