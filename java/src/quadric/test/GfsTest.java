package quadric.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.Test;

import quadric.gfs.Gfs;
import quadric.gfs.GfsDateUtil;
import quadric.gfs.GfsManager;
import quadric.gfs.GfsMask;
import quadric.gfs.GfsPolicy;

public class GfsTest {
	@Test
	public void testGuid() {
		for(int x = 0; x < 100; ++x) {
			runIt();
			System.out.println("Test " + x + "  PASS");
		}
	}
	
	private void runIt() {
		List<GfsPolicy> pol = createTestPolicy();
		String radly = pol.stream().map( p -> p.toString()).collect(Collectors.joining("\n"));
		System.out.println(radly);
		SortedSet<Long> backups = createBackups();
		SortedMap<Long,Integer> pooled = new TreeMap<Long,Integer>();
		GfsManager.instance().getPooledVersions(pol, backups, pooled, 0);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EE, MM dd YYYY");
		System.out.println("DATE START: " + GfsDateUtil.fromEpoch(backups.first()).format(formatter));
		System.out.println(" DATE END: " + GfsDateUtil.fromEpoch(backups.last()).format(formatter));
		int [] genCount = new int[4];
		for(Map.Entry<Long,Integer> e : pooled.entrySet()) {
			LocalDate killer = GfsDateUtil.fromEpoch(e.getKey());
			String desc = GfsMask.toStringList(e.getValue());
			if(desc.contains("yearly")) {
				genCount[0]++;
			} 
			if(desc.contains("monthly")) {
				genCount[1]++;
			}
			if(desc.contains("weekly")) {
				genCount[2]++;
			} 
			if(desc.contains("daily")) {
				genCount[3]++;
			} 
			System.out.println(killer.format(formatter) + "\t\t" + desc);
		}

		assertTrue(pol.get(0).versions == genCount[3]);
		assertTrue(pol.get(1).versions == genCount[2]);
		assertTrue(pol.get(2).versions == genCount[1]);
		assertTrue(pol.get(3).versions == genCount[0]);

	}
		
	public SortedSet<Long> createBackups() {
		Random rando = new Random();
		int max = rando.nextInt(100);
		max += 2000;
		List<Long> shlong = new ArrayList<Long>();
		LocalDate d = GfsDateUtil.fromEpoch(System.currentTimeMillis() / 1000L);
		for(int x = 0; x < max; ++x) {
			d = d.minusDays(1);
			shlong.add(GfsDateUtil.toEpoch(d));
		}
		return new TreeSet<Long>(shlong);
		
	}
	
	public List<GfsPolicy> createTestPolicy() {
		Random rando = new Random();
		List<GfsPolicy> pols = new ArrayList<GfsPolicy>();
		GfsPolicy pol = new GfsPolicy();
		pol.card = rando.nextInt(2);
		pol.gfs = Gfs.gfsDaily;
		pol.versions = rando.nextInt(3) +1;
		pols.add(pol);
		
		pol = new GfsPolicy();
		pol.card = rando.nextInt(7);
		//pol.card = 1;
		pol.gfs = Gfs.gfsWeekly;
		//pol.versions = 20;
		pol.versions = rando.nextInt(3) +1;
		pols.add(pol);
		
		pol = new GfsPolicy();
		if(rando.nextBoolean() == true) {
			pol.card = rando.nextInt(4);
			//pol.card = 1;
			pol.gfs = Gfs.gfsMonthly;
		} else {
			pol.card = rando.nextInt(2);
			pol.gfs = Gfs.gfsMonthlyDaily;
		}
		pol.versions = rando.nextInt(10) +1;
		//pol.versions = 10;
		pols.add(pol);
		
		pol = new GfsPolicy();
		pol.card = rando.nextInt(12);
		pol.gfs = Gfs.gfsYearly;
		pol.versions = rando.nextInt(4) +1;
		pols.add(pol);
		
		return pols;
		
	}
}
