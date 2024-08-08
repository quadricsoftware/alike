package quadric.gfs;

import java.util.ArrayList;

public enum GfsMask {
		gfsDailyMask(0x01),
		gfsWeeklyMask(0x02),
		gfsMonthlyMask(0x04),
		gfsYearlyMask(0x08),
		gfsAdHocMask(0x10),
		gfsCandidateMask(0x20),
		gfsEmptyMask(0x00);
	
		public final int mask;
		
		private GfsMask(int val) {
			this.mask = val;
		}
		
		public static GfsMask gfsToMask(Gfs gfs) {
			switch(gfs) {
				case gfsDaily: return gfsDailyMask;
				case gfsWeekly: return gfsWeeklyMask;
				case gfsMonthly: case gfsMonthlyDaily: return gfsMonthlyMask;
				case gfsYearly: return gfsYearlyMask;
				case gfsAdHoc: return gfsAdHocMask;
				case gfsCandidate: return gfsCandidateMask;
				default:
					return gfsEmptyMask;
			}
		}
		
		public static String [] toList(int bitty) {
			ArrayList<String> awesome = new ArrayList<String>();
			if((bitty & gfsDailyMask.mask) != 0) {
				awesome.add("daily");
			} 
			if((bitty & gfsWeeklyMask.mask) != 0 ) {
				awesome.add("weekly");
			}
			if((bitty & gfsMonthlyMask.mask) != 0 ) {
				awesome.add("monthly");
			}
			if((bitty & gfsYearlyMask.mask) != 0 ) {
				awesome.add("yearly");
			}
			if((bitty & gfsAdHocMask.mask) != 0 ) {
				awesome.add("basic");
			}
			if((bitty & gfsCandidateMask.mask) != 0 ) {
				awesome.add("candidate");
			}
			if(awesome.isEmpty()) {
				awesome.add("none");
			}
			return awesome.toArray(new String [0]);
		}
		
		public static String toStringList(int bitty) {
			return String.join(",", toList(bitty));
		}
}


