package quadric.ods;

import java.util.function.DoubleConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.util.HclWriterUtil;
import quadric.util.JobControl;
import quadric.util.Print;

public class NoOpBlockSource implements BlockSource {
	private static final Logger LOGGER = LoggerFactory.getLogger( NoOpBlockSource.class.getName() );
	
	int dsNum;
	CheckType checkType;
	int lastCheck = 1;
	int curBlock = -1;
	
	public NoOpBlockSource(int dsNum) {
		this.dsNum = dsNum;
		this.checkType = VaultSettings.instance().getCheckType();
		if(checkType != CheckType.none) {
			LOGGER.info("Checking blocks at " + checkType.name() + " frequency");
		} else {
			LOGGER.debug( "Only first block will be checked...");
		}
		
	}
	
	@Override
	public byte[] getBlock(Print p) {
		curBlock++;
		if(	(checkType == CheckType.full) 
				|| (curBlock == 0)
				|| (checkType == CheckType.quadradic && curBlock == (lastCheck * lastCheck * lastCheck * lastCheck))
				) {
			lastCheck++;
			CloudAdapter adp = VaultSettings.instance().getAdapter(dsNum);
			if(adp.stat(p.toString()) == false) {
				throw new CloudException("Block verification check failed for " + p);
			}
		}
		return null;
		
	}

	@Override
	public void load(HclWriterUtil neededPrints, DoubleConsumer progress, JobControl c) {
		progress.accept(100);
	}

	@Override
	public int count() {
		return 0;
	}

	@Override
	public String getBlockPath(Print p) {
		return null;
	}

	
}
