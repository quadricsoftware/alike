package quadric.restore;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.VaultSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

public class VmVersionHierarchy {
	private static final Logger LOGGER = LoggerFactory.getLogger( VmVersionHierarchy.class.getName() );
	
	private String path;
	private String [] splits;
	
	public VmVersionHierarchy(String path) {
		this.path = path;
		splits = path.split("/", -1);
	}
	
	public int getSite() {
		return Integer.parseInt(splits[1]);
	}
	
	public String getVmName() {
		String name = getVmFolder();
		String [] splits = name.split("_");
		return splits[1];
	}
	
	public int getVmNameQualifier() {
		String [] splits = getVmFolder().split("_");
		return Integer.parseInt(splits[0]);
	}
	
	public int getDepth() {
		int depthCharge = splits.length -1;
		if(path.equals("/")) {
			depthCharge = 0;
		}
		
		//LOGGER.trace("Depth is " + depthCharge + " for path " + path);
		return depthCharge;
	}
	
	public String getVmFolder() {
		return splits[2];
	}
	
	public String getVmPath() {
		return splits[0] + "/" + splits[1] + "/" + splits[2];
	}
	
	public String getVmVersionPath() {
		return splits[0] + "/" + splits[1] + "/" + splits[2] + "/" + splits[3];
	}
	
	public String getVersionFolder() {
		return splits[3];
	}
	
	public String getDiskPart() {
		return splits[4];
	}
	
	public String getParent() {
		if(getDepth() == 0) return null;
		File f = new File(path);
		return f.getParent();
	}
	
	public int getDiskNum() {
		if(getDiskPart().contains(".")) {
			return Integer.parseInt(getDiskPart().split("\\.")[0]);
		}
		return Integer.parseInt(getDiskPart());
	}
	
	public boolean isDirectory() {
		boolean isDir = new File(path).getName().contains(".") == false;
		//LOGGER.trace("Examining path " + path + " to see if its a dir or not: " + isDir);
		return isDir;
		
	}
	
	public boolean isFlrMount() {
		if(getDepth() == 4) {
			if(getDiskPart().contains(".") == false) {
				return true;
			}
		}
		return false;
	}
	
	public String getSymLinkPathForFlr() {
		return VaultSettings.instance().getFlrMountBase() + "/" + getSite() + "_" + getVmNameQualifier() + "_" + getVersionFolder() + "_" + getDiskNum();
	}
	
	@Override
	public String toString() {
		return path;
	}
	
}
