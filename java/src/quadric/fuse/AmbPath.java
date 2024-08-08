package quadric.fuse;

public class AmbPath {
	public int jobId;
	public int vmId;
	public int diskNum;
	
	public AmbPath(String path) {
		path = shorten(path, 4);
		diskNum = Integer.parseInt(path.split("_")[3]);
		jobId = Integer.parseInt(path.split("_")[0]);
		vmId = Integer.parseInt(path.split("_")[1]);
		
	}
	
	public static String shorten(String path) {
		return shorten(path, 2);
	}
	
	public static String shorten(String path, int max) {
		if(path.startsWith("/") == false || path.toLowerCase().endsWith(".amb") == false) {
			throw new IllegalArgumentException("Bad path: " + path + " does not start with slash or end with .amb");
		}
		path = path.substring(1);
		// Get rid of ".amb" part
		path = path.split("\\.")[0];
		String [] splitz = path.split("_");
		if(splitz.length < 2) {
			throw new IllegalStateException("AMB file " + path + " not splittable");
		}
		StringBuilder bob = new StringBuilder();
		for(int x = 0; x < max; ++x) {
			bob.append(splitz[x]);
			if(x+1 < max) {
				bob.append("_");
			}
		}
		return bob.toString();
			//return splitz[0] + "_" + splitz[1];
	}
	
	public static String makeAmbPath(int jobId, int vmId) {
		return jobId + "_" + vmId;
	}

}
