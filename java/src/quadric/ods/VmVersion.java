package quadric.ods;

import java.util.ArrayList;
import java.util.List;

import quadric.util.VaultUtil;

public class VmVersion {
	private long version;
	private String uuid;
	private String vmName;
	private String metaData;
	int virtualType;
	List<Long> diskSizes = new ArrayList<Long>();
	private long vaultId;
	private int siteId;
	
	public VmVersion() { ;}
	
	
	public VmVersion(VmVersion v2) {
		this.version = v2.version;
		this.uuid = v2.uuid;
		this.vmName = v2.vmName;
		this.metaData = v2.metaData;
		this.virtualType = v2.virtualType;
		this.diskSizes = v2.diskSizes;
		this.vaultId = v2.vaultId;
		this.siteId = v2.siteId;
	}
	
	public VmVersion(int siteId, long version, String uuid, String vmName, String metaData, int virtualType, List<Long> diskSizes) {
		this.version = version;
		this.uuid = uuid;
		this.vmName = vmName;
		this.metaData = metaData;
		this.virtualType = virtualType;
		this.diskSizes = diskSizes;
		this.siteId = siteId;
	}
	
	public long getVersion() {
		return version;
	}
	public void setVersion(long version) {
		this.version = version;
	}
	public String getNormalizedUuid() {
		return uuid;
	}
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	public String getVmName() {
		return vmName;
	}
	public void setVmName(String vmName) {
		this.vmName = vmName;
	}
	public String getMetaData() {
		return metaData;
	}
	public void setMetaData(String metaData) {
		this.metaData = metaData;
	}
	public long getTotalSize() {
		return diskSizes.stream().mapToLong(i -> i).sum();
		
	}

	public List<Long> getDiskSizes() {
		return diskSizes;
	}
	
	public void setDiskSizes(List<Long> diskSizes) {
		this.diskSizes = diskSizes;
	}
	
	public int getVirtualType() {
		return virtualType;
	}
	public void setVirtualType(int virtualType) {
		this.virtualType = virtualType;
	}


	public long getVaultId() {
		return vaultId;
	}


	public void setVaultId(long k) {
		this.vaultId = k;
	}


	public int getSiteId() {
		return siteId;
	}


	public void setSiteId(int siteId) {
		this.siteId = siteId;
	}
	
	public void setPlatformStyleUuid(String uuid) {
		String u2 = uuid.replace("-", "");
		this.uuid = u2.toUpperCase();
		
	}
	
	public static String unfixUuid(final String orig) {
		 
		StringBuilder sb = new StringBuilder(orig);
		sb.insert(8, '-');
		sb.insert(13, '-');
		sb.insert(18, '-');
		sb.insert(23, '-');
		return sb.toString();
	}
	
	public String getPlatformStyleUuid() {
		String awesome = unfixUuid(this.uuid);

		if(virtualType == 3) {
			// ONLY HV is this way?
			return awesome.toUpperCase();
		} else {
			// All others lowercase we guess
			return awesome.toLowerCase();
			
		}
	}
	
	public static int guessVirtType(String platformStyleUuid) {
		for(int x = 0; x < platformStyleUuid.length(); ++x) {
			if(Character.isUpperCase(platformStyleUuid.charAt(x))) {
				return 3;
			} 			
		}
		return 2;
	}
	
	@Override
	public String toString() {
		return "Version " + vmName + " with uuid: "+ getPlatformStyleUuid() + " from " + version + " on site " + siteId;
	}


}
