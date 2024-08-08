package quadric.ods;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

import quadric.blockvaulter.CloudException;
import quadric.util.HclCursor;
import quadric.util.HclReaderUtil;
import quadric.util.Print;
import quadric.util.VaultUtil;

public class BinarySearchFile {	
	private String path;
	private int skipBytes;
	
	
	
	public BinarySearchFile(String path, int skipBytes) {
		this.path = path;
		this.skipBytes = skipBytes;
	}
	
	public HclCursor createCursor() {
		HclReaderUtil reader = new HclReaderUtil(path, skipBytes);
		return reader.createCursor();
	}
	
	
	public boolean searchBeforeCursor(Print cord, int position) {
		if(position == 0) {
			return false;
		}
		try (FileInputStream fis = new FileInputStream(path)) {
			return VaultUtil.binarySearch(cord, fis.getChannel(), position -1, skipBytes, true) != null;
		} catch(Exception e) {
			throw new CloudException(e);
		}	
	}
	
	

	public void delete() {
		File file = new File(path);
		file.delete();
	}
	
	
	

}
