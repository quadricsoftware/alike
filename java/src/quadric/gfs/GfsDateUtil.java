package quadric.gfs;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

import quadric.blockvaulter.CloudException;

public class GfsDateUtil {
	public static final ZoneId UTC_ZONE = ZoneId.ofOffset("UTC", ZoneOffset.ofHours(0));
	
	public static LocalDate getPreviousSunday(LocalDate d) {
		while(d.getDayOfWeek().equals(DayOfWeek.SUNDAY) == false) {
			d = d.minus(1, ChronoUnit.DAYS);
		}
		return d;
	}
	
	public static void sanityCheckDate(LocalDate d) {
		long maybe = toEpoch(d);
		long now =  (System.currentTimeMillis() / 1000);
		long futureProof = (507L * 60L * 60L * 24L * 365L);
		if(maybe > futureProof) {
			throw new CloudException("Timestamp is out of range");
		}
		if(maybe < 0) {
			throw new CloudException("Timestamp is out of range");
		}
		int diff = Math.abs( (int) (now - maybe));
		int thousandYears = 60 * 60 * 24 * 365 * 1000;
		if(thousandYears < diff) {
			throw new CloudException("Timestamp is illegal");
		}
		
	}
	
	public static LocalDate fromEpoch(long epochSecs) {
		return Instant.ofEpochSecond(epochSecs).atZone(UTC_ZONE).toLocalDate();
	}
	
	public static long toEpoch(LocalDate d) {
		return d.atStartOfDay(UTC_ZONE).toEpochSecond();
	}
	
	public static void sanityCheckDate(long epochSecs) {
		sanityCheckDate(fromEpoch(epochSecs));
	}
	
	public static int getDayOfWeek(LocalDate d) {
		int foo = d.getDayOfWeek().get(ChronoField.DAY_OF_WEEK);
		if(foo == 7) {
			foo = 0;
		}
		return foo;
	}
	
	public static LocalDate roundToNearestDay(LocalDate d) {
		return LocalDate.of(d.getYear(), d.getMonth(), d.getDayOfMonth());
	}
	
	public static String instaFormat(long epochSex) {
		LocalDate d = fromEpoch(epochSex);
		return "" + d.toString() + " " + d.getDayOfWeek();
	}

}
