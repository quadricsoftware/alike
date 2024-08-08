package quadric.util;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.KurganCommander.Dispatch;
import quadric.ods.Ods;
import quadric.spdb.ConsistencyException;
import quadric.spdb.KurganBlock;

public class ValidationHelper {
	public void doValidateBlock(Print p, Ods ds) throws FileNotFoundException {		
		try {
			GetResult rezzy = ds.getCloudAdapter().getBlock(p.toString(), 0);
			KurganBlock blockHead = KurganBlock.create(rezzy, p.toString(), true);
			if(blockHead.getMd5().equals(p.toString()) == false) {
				throw new ConsistencyException("MD5 of print " + p + " failed");
			}
		} catch(CloudException ce) {
			Throwable ee = ce.getCause();
			if(ee != null && ee instanceof FileNotFoundException) {
				throw new FileNotFoundException();
			}
			throw ce;
		} catch(IOException ioe) {
			if(ioe instanceof FileNotFoundException) {
				throw new FileNotFoundException();
			}
			throw new CloudException(ioe);
		}
		
	}

	public void dumpValidateReport(Dispatch d, List<Print> badBlocks, List<Print> messedUpPrints, List<Print> missingBlocks) {
		String path = d.getJsonParam("errorFile");
		String pathTemp = path + ".tmp";
		Report porto = new Report();
		porto.badBlocks = badBlocks;
		porto.errorBlocks = messedUpPrints;
		porto.missingBlocks = missingBlocks;
		ObjectMapper mapper = new ObjectMapper();
		try {
			String json = mapper.writeValueAsString(porto);
			try (FileWriter fw = new FileWriter(pathTemp)) {
				fw.write(json);
			}
			Files.move(Paths.get(pathTemp), Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
		} catch(Exception e) {
			throw new CloudException(e);
		}
		
	}
	
	
	public static class Report {
		List<Print> badBlocks;
		List<Print> errorBlocks;
		List<Print> missingBlocks;
		
		public List<String> getBadBlocks() {
			return badBlocks.stream().map(Print::toString).collect(Collectors.toList());
		}
		
		public List<String> getMissingBlocks() {
			return missingBlocks.stream().map(Print::toString).collect(Collectors.toList());
		}
		
		public List<String> getErrorBlocks() {
			return errorBlocks.stream().map(Print::toString).collect(Collectors.toList());
		}
	}
}

