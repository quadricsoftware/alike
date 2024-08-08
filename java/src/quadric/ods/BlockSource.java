package quadric.ods;

import java.util.function.DoubleConsumer;

import quadric.util.HclWriterUtil;
import quadric.util.JobControl;
import quadric.util.Print;

/**
 * Represents source of block data that may or may not be itself a datasource
 *
 */
public interface BlockSource {
	public byte [] getBlock(Print p);
	
	/**
	 * Creates a sorted list of all unique prints from the ECL and then creates a lookup binary file
	 * from that containing all offsets into the AMB
	 * @param progress
	 * @param c
	 */
	public void load(HclWriterUtil neededPrints, DoubleConsumer progress, JobControl c);
	
	/**
	 * The total number of blox
	 * @return
	 */
	public int count();
	
	
	/**
	 * Returns the filesystem path of the block or null if this blocksource doesn't expose a filesystem.
	 * @param p
	 * @return
	 */
	public String getBlockPath(Print p);
}
