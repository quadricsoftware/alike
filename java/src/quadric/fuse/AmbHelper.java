package quadric.fuse;

import quadric.blockvaulter.DataStores;
import quadric.fuse.AmbMonitor.AmbStats;
import quadric.ods.Ods;
import quadric.spdb.KurganBlock;
import quadric.spdb.KurganBlock.BlockSettings;
import quadric.util.Print;
import quadric.util.VaultUtil;

public class AmbHelper {

	private static final int ADS_SITE_ID = 0;
	
	public static int getOrRegister(String path) {
		int txNo = -1;
		String shorty = AmbPath.shorten(path);
		Ods ds = DataStores.instance().getOds(ADS_SITE_ID);
		AmbStats stats = new AmbStats();
		txNo = ds.getVaultManager().registerMunge(shorty, stats);
		return txNo;
	}
	
	public static void doWriteBlock(BlockSettings bs, KurganBlock kb, String path, int txNo) {
		if(bs.shouldEncyptNewBlocks) {
			// Add encyption to the source block
			int oldHeader = kb.getBlockHeader();
			String fiver = kb.getMd5();
			byte [] payloadSansHeader = kb.getPayloadStrippedOfHeader();
			int header = KurganBlock.generateFlags(
								false, 	
								true
								);
			kb = new KurganBlock(payloadSansHeader, bs.blockPassword, header, fiver);
			// Manually override
			int newHeader = oldHeader | KurganBlock.ENCRYPT_FLAG_AES; 
			kb.setBlockHeader(newHeader);
					
			
		}
		
		Ods ds = DataStores.instance().getOds(ADS_SITE_ID);
		Print p = new Print(kb.getMd5());
		AmbStats stats = (AmbStats) ds.getVaultManager().lockPrint(txNo, p, null);
		boolean isDamaged = false;
		if(ds.revealBadBlockManager().isDamaged(p)) {
			isDamaged = true;
			ds.getCloudAdapter().del(p.toString());
		}
		// Update sent stats
		AmbPath tardy = new AmbPath(path);
		String rez = VaultUtil.putBlockFromMem(kb.getPayload(), kb.getSize(), ds.getCloudAdapter(), kb.getMd5());
		// Deal with global data dedup
		if(rez == null) {
			stats.blocksSkipped[tardy.diskNum]++;
			stats.bytesSent[tardy.diskNum]+= kb.getSize();
		} else {
			if(isDamaged) {
				ds.revealBadBlockManager().heal(p);
			}
			ds.sentBlock(kb.getSize());
			stats.blocksSent[tardy.diskNum]++;
			stats.bytesSent[tardy.diskNum]+= kb.getSize();
		}
	}
}
