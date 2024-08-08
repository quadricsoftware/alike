package quadric.vhdx;

import java.io.ByteArrayInputStream;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Interpolator {
	private static final Logger LOGGER = LoggerFactory.getLogger( Interpolator.class.getName() );
	
	public static interface Interpolate {
		int read(byte [] data, long offset, int start, int amt);
		long regionSize();
	}
	
	
	static abstract class MemInterpolate implements Interpolator.Interpolate {

		@Override
		public int read(byte[] data, long offset, int start, int amt) {
			byte [] chompy = getBuffer();
			ByteArrayInputStream peepee = new ByteArrayInputStream(chompy);
			if(peepee.skip(offset) != offset) {
				throw new IllegalArgumentException("Unable to skip bytes");
			}
			return peepee.read(data, start, amt);
		}
		
		public abstract byte [] getBuffer();	
	}
	
	public static class MemCommand extends MemInterpolate {
		private final Supplier<byte []> supplier;
		private final Supplier<Long> supplier2;
		public MemCommand(Supplier<byte []> supplier, Supplier<Long> supplier2) {
			this.supplier = supplier;
			this.supplier2 = supplier2;
		}
		
		@Override
		public byte [] getBuffer() {
			return this.supplier.get();
		}
		
		@Override
		public long regionSize() {
			return supplier2.get();
		}
		
	}
	 
	private SortedMap<Long,Interpolate> regions = new TreeMap<Long,Interpolate>(); 
	private String name;
	
	public Interpolator(String name) {
		this.name = name;
	}
	
	public void register(Interpolate t, long offset) {
		regions.put(offset, t);
	}
	
	public int read(byte [] data, long offset, int start, int amt) {
		LOGGER.trace("Call is at offset: " + offset + " with start: " + start + " and amt: " + amt);
		int x = 0;
		while(x < amt) {
			long curPos = offset + x;
			// Determine if there is a region behind us
			// that this offset lies within
			SortedMap<Long,Interpolate> posteriorView = regions.headMap(curPos);
			if(posteriorView.isEmpty() == false) {
				long previousOffset = posteriorView.lastKey();
				Interpolate p = posteriorView.get(previousOffset);
				if((offset + x) < p.regionSize() + previousOffset) {
					long relativeOffsetIntoRegion = curPos - previousOffset;
					int killer = p.read(data, relativeOffsetIntoRegion, start + x, amt -x);
					/* LOGGER.debug(name + " reading at offset " +
							+ curPos + " found posterior region with offset " 
							+ previousOffset + " and length " + p.regionSize()
							+ " and read " + killer + " off of it from relative offset " + relativeOffsetIntoRegion); */
					x += killer;
					continue;
				}
			}
			// See if there is a region in front of us
			// and determine if we need to skip to the lou
			int skipCount = 0;
			SortedMap<Long,Interpolate> anteriorView = regions.tailMap(curPos);
			if(anteriorView.isEmpty()) {
				// Oh snap we've reached the end
				x = amt;
				break;
			}
			Long firstKey = anteriorView.firstKey();
			if(firstKey == curPos) {
				// We have the right key, read from it
				Interpolate p = regions.get(firstKey);
				int killer = p.read(data, 0, start + x, amt -x);
				/*LOGGER.debug(name + " while reading at offset " + curPos 
						+ " fulfilled " + killer + " from region at offset " + firstKey
						+ " of size " + p.regionSize()); */ 
				x+= killer;
				continue;
			}
			// the next offset is a little ahead of us
			skipCount = (int) (firstKey - curPos);
			
			if(amt - x < skipCount) {
				/// don't skip out of bounds
				skipCount = amt -x;
			}
			//LOGGER.debug(name + " skipping from offset " + curPos + " by " + skipCount + " to offset " + (curPos + skipCount));
			x+= skipCount;
		}
		return x;
	}
	
}
