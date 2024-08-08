package quadric.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import quadric.vhdx.BitTweaker;
import quadric.vhdx.Guid;
import quadric.vhdx.KnownGuids;
import quadric.vhdx.VhdxBatEntry;
import quadric.vhdx.VhdxHeader;
import quadric.vhdx.VhdxRegionTableEntry;
import quadric.vhdx.VhdxRegionTableHeader;

public class VhdxTest {
	@Test
	public void testGuid() {
		try {
			Guid g1 = new Guid(KnownGuids.BAT_GUID);
			System.out.println(g1.toHex());
			byte [] crap = g1.store();
			Guid g2 = new Guid();
			g2.load(crap);
			System.out.println(g2.toHex());
			assertEquals(g1.toHex(), g2.toHex());
		} catch(Throwable t) {
			t.printStackTrace();
			throw t;
		}
		
		
	}
	
	@Test
	public void testBits() {
		//int eek = 0xFFFFFFFF;
		int eek = 6;
		BitTweaker tw = new BitTweaker(new byte[8]);
		tw.insertBits(0, 3, eek);
		long f2 = tw.extractBits(0,3);
		long f3 = tw.extractBits(3,4); 
		tw.insertBits(17, 44, 12804040);
		long f5 = tw.extractBits(17, 44);
		f2 = tw.extractBits(0, 3);
		System.out.println(f2);
		
		
		
	}
	
	@Test
	public void testHeader() {
		VhdxHeader give = new VhdxHeader();
		give.set02Checksum(10);
		give.set03SequenceNumber(10);
		give.set04FileWriteGuid(new Guid(KnownGuids.BAT_GUID));
		
		VhdxHeader give2 = new VhdxHeader();
		byte [] byteMe = give.store();
		give2.load(byteMe);
		System.out.println("Guid1: " + give.get04FileWriteGuid().toHex() + " vs " + give2.get04FileWriteGuid().toHex());
		assertTrue(Arrays.equals(give.store(), give2.store()));
		
		VhdxRegionTableEntry dinner = new VhdxRegionTableEntry();
		dinner.set1Guid(new Guid(KnownGuids.BAT_GUID));
		dinner.set2FileOffset(1024 * 1024);
		dinner.set3Length(1024 * 1024);
	}
}
