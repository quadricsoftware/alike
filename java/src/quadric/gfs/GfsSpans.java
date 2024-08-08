package quadric.gfs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class GfsSpans {
	public TreeMap<Long, List<Long>> spans = new TreeMap<Long, List<Long>>();
	
	public static long getSpanStart(long timeInSpan, Gfs gfs) {
		LocalDate gdate = GfsDateUtil.fromEpoch(timeInSpan);

		switch(gfs) {
			case gfsDaily:
				break;
			case gfsWeekly: 
				gdate = GfsDateUtil.getPreviousSunday(gdate);
				break;
			case gfsMonthly:
			case gfsMonthlyDaily:
				gdate = gdate.withDayOfMonth(1);
				gdate = GfsDateUtil.roundToNearestDay(gdate);
				break;
			case gfsYearly:
				gdate = gdate.withMonth(1);
				gdate = gdate.withDayOfMonth(1);
				gdate = GfsDateUtil.roundToNearestDay(gdate);
				break;
			default:
				// A VERY short timespan
				return timeInSpan;
		}
		return GfsDateUtil.toEpoch(gdate);
	}
	
	public List<Long> get(long spanAt) {
		List<Long> shlong = spans.get(spanAt);
		if(shlong == null) {
			shlong = new ArrayList<Long>();
			spans.put(spanAt, shlong);
		}
		return shlong;
	}
}
