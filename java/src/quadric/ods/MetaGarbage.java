package quadric.ods;

public class MetaGarbage {
	public MetaGarbage(String installId, long jobId, long vmId) {
		this.installId = installId;
		this.jobId = jobId;
		this.vmId = vmId;
	}
	
	String installId;
	long jobId;
	long vmId;
	
	@Override
	public String toString() {
		return "MetaG [installId=" + installId + ", jobId=" + jobId + ", vmId=" + vmId + "]";
	}
	
	
}
