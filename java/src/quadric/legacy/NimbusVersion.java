package quadric.legacy;

import java.text.SimpleDateFormat;

import quadric.ods.VmVersion;

/**
 * A specialized version that's compatable with legacy land
 *
 */
public class NimbusVersion extends VmVersion {
	private long jobId;
	private long vmId;
	
	public NimbusVersion(VmVersion v) {
		super(v);
	}
	
	
	public NimbusVersion() {
	}


	public long getJobId() {
		return jobId;
	}
	public void setJobId(long jobId) {
		this.jobId = jobId;
	}
	public long getVmId() {
		return vmId;
	}
	public void setVmId(long vmId) {
		this.vmId = vmId;
	}	

	@Override
	public String toString() {
		SimpleDateFormat fd = new SimpleDateFormat("MMM-dd-yyyy HH:mm:ss");
		String friendly = fd.format(getVersion() * 1000);
		return "NimbusVersion uuid: " + getNormalizedUuid() 
		+ " timestamp: " + getVersion() 
		+ " (" + friendly + ") jobId: " + getJobId() 
		+ " vmid: " + getVmId()
		+ " siteId: " + getSiteId();
	}
}
