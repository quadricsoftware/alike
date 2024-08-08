package quadric.ods;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.VaultSettings;
import quadric.crypt.CryptUtil;
import quadric.ods.dao.Crud;
import quadric.ods.dao.Dao;
import quadric.ods.dao.DaoFactory;
import quadric.ods.dao.DsInfoCrud;
import quadric.ods.dao.FlagCrud;
import quadric.ods.dao.UuidPairCrud;
import quadric.ods.dao.VmVersionCrud;
import quadric.util.ByteStruct;
import quadric.util.CloseableIterator;
import quadric.util.Flocker;
import quadric.util.JobControl;
import quadric.util.Pair;
import quadric.util.PathedGetResult;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;

/**
 * Manages  transact.tx and flags lifecycle. Manages deletion of ecls as well. 
 * Flags are kept in an LRU to save memory (at ~20 bytes a pop), but the transact.tx is kept in memory at all times,
 * (at about 12 bytes a pop-- a million entries would be about 12MB of RAM).
 * Doesn't deal with vm metainformation the way the c++ version used to; VaultCache does that.
 *
 */
public class HeaderManager {
	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
	    Set<Object> seen = ConcurrentHashMap.newKeySet();
	    return t -> seen.add(keyExtractor.apply(t));
	}
	
	@FunctionalInterface
	public interface FlagConsumer {
		public void accept(EclFlags flags, double currentProgress);
	}
	
	public static class HeaderEvent {
		public HeaderEventType type;
		public VmVersion version;
		public long changeSize = 0;
		public int diskNum = 0;
	}
	
	public enum HeaderEventType {
		changed,
		deleted,
		updatePreDedupSize,
		updatePostDedupSize,
		updateFullSize,
		updateJobOrig,
		updateJobDelta,
		fullSync,
		maxVersionsChange
	}
	
	private static final int BROKEN_TX_WINDOW_MINS = 30;
	private static final int ODS_SEQ_OFFSET = 60;
	private static final Logger LOGGER = LoggerFactory.getLogger( HeaderManager.class.getName() );
	private static final int TRANSACT_TX_FILE_VERSION = 1;
	
	
	protected Dao dao;
 	long largestTx = 0;
 	int siteId;
 	Set<Consumer<HeaderEvent>> listeners = Collections.newSetFromMap(new WeakHashMap<Consumer<HeaderEvent>, Boolean>());
 	boolean paranoidFlags;
 	volatile double globalProgress;
 	Stopwatch txBackupTimer = new Stopwatch();
	
 	/**
 	 * Constructs a new HeaderManager based on the bandwidth meter specified
 	 * @param adp
 	 * @param band
 	 */
	public HeaderManager(int siteId) {
		// 
		//
		//
		//
		//
		//
		//
		this.paranoidFlags = VaultSettings.instance().isParanoid();
		//
		//
		//
		//
		
		LOGGER.info("HeaderManager initializing for siteId " + siteId);
		this.siteId = siteId;
		String pathy = VaultSettings.instance().getRemoteDbPath() + "/cache.db";
		this.dao = DaoFactory.instance().create(pathy);
		checkAndMakeDsEntry();	
	}
	
	/**
	 * Nukes all cached information about a particular vault site
	 */
	public synchronized void clear() {
		try (Connection con = dao.getWriteConnection()) {
			DsInfoCrud crud = new DsInfoCrud(con);
			crud.buildQuery("DELETE FROM vversion WHERE siteId=?",siteId);
			crud.buildQuery("DELETE FROM flag WHERE siteId=?", siteId);
			crud.buildQuery("DELETE FROM vversion_disk WHERE siteId=?", siteId);
			con.commit();
			// Everything has changed
			fireVmChanged(null, HeaderEventType.fullSync);
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public double rebuildProgress() {
		return globalProgress;
	}
	
	/**
	 *  Adds an asyc background listener for updates (possibly spurious) to Header events
	 */
	public synchronized void addListener(Consumer<HeaderEvent> listener) {
		listeners.add(listener);
	}
	
	/**
	 * Loads everything into database cache
	 */
	public synchronized void load(DoubleConsumer progress) {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		// Check if there's actually anything to do
		if(adp.stat("transact.tx") == false) {
			progress.accept(100);
			return;
		}
		List<VmVersion> changedVersions = new ArrayList<VmVersion>();
		Connection con = null;
		try (CloseableIterator<TxEntry> i = loadTransactFile()) {
			
			progress.accept(0);
			double mine = 0.0;
			while(i.hasNext()) {
				if((con != null) && (mine % 1000) == 0) {
					LOGGER.debug("Periodic reset of SQLite connection");
					try {
						con.commit();
						FlagCrud dummy = new FlagCrud(con);
						try {
							dummy.buildTempQuery("pragma wal_checkpoint(truncate)");
						} catch(Throwable t) {
							LOGGER.debug("Truncate failed, still will close connection");
						}
						con.close();
						con = null;
						try {
							// Give a little airtime to dbsync etc
							Thread.sleep(200);
						} catch(Throwable t) { ;}
					} catch(Throwable t) {
						LOGGER.error("Problem closing connection", t);
					}
				}
				if(con == null) {
					con = dao.getWriteConnection();
				}
				FlagCrud crud = new FlagCrud(con);
				TxEntry e = i.next();
				EclFlags flags = materializeFromOds(e, adp);
				if(flags == null) {
					continue;
				}
				// Write it to DB
				crud.create(flags);
				// Handle metadata from ECL,
				// but only for actual saved versions
				if(flags.getDeleteTx() == 0   
							&& flags.getState() > TransactState.TransactActive.getValue()) {
					VmVersion vvv = null;
					try {
						vvv = loadAndStoreMeta(flags, con);
					} catch(CloudException ce) {
						LOGGER.error("Load of DataStore " + siteId + " unable to load metainformation for flag " + flags + " with problem: " + ce.getMessage());
						continue;
					}
					changedVersions.add(vvv);
				} else if(flags.getDeleteTx() != 0){
					VmVersion vvv = changedVersions.stream().filter(v -> v.getVaultId() == flags.getDeleteTx()).findFirst().orElse(null);
					if(vvv != null) {
						// Kill the insert we made earler
						killMetaForDeleteTx(con, flags.getDeleteTx());
						// Don't send updates about this guy, he's already dead
						changedVersions.remove(vvv);
					} else {
						// Since the transact.tx file is in order, this shouldnt happen.
						LOGGER.error("Delete transaction "  + flags.getTxNo() 
										+ " wants to delete tx " + flags.getDeleteTx() + " but it doesnt exist!");
					}
				}
				double ferkface = ((double) ++mine / i.count()) * 100.00;
				progress.accept(ferkface);
				LOGGER.debug("Gathered meta of " + mine + " of " + i.count());
			}
			progress.accept(100);
			//fireVmsChanged(changedVersions, HeaderEventType.changed);
		} catch(Exception e) {
			throw new CloudException(e);
		} finally {
			if(con != null) {
				try { 
					con.commit();
					con.close();
				} catch(Throwable t) {
					LOGGER.error("Problem closing connection", t);
				}
			}
		}
		verifyFlags();
	}
	
	public synchronized void verifyFlags() {
		if(paranoidFlags == false) {
			return;
		}
	
		LOGGER.info("Verifying flags state");
	
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		// Check if there's actually anything to do
		if(adp.stat("transact.tx") == false) {
		
			return;
		}
		Set<Long> mySets = new HashSet<Long>();
		try (CloseableIterator<TxEntry> i = loadTransactFile(); 
							Connection con = dao.getReadOnlyConnection()) {
		
			FlagCrud crud = new FlagCrud(con);
			while(i.hasNext()) {
				TxEntry e = i.next();
				EclFlags flags = materializeFromOds(e, adp);
				if(flags == null) {
					continue;
				}
				EclFlags flags2000 = crud.querySingle(crud.buildQuery("SELECT * FROM flag WHERE txNo=? AND siteId=?", e.tx, siteId));
				if(flags2000 == null) {
					LOGGER.error("Validation of flags failed. " + flags + " not found in local cache");
					System.exit(0);
				}
				if(flags.getOwnerTs() == -1 && flags2000.getState() == TransactState.TransactBegin.getValue()) {
					// This is ok, it means the .flags file wasn't written yet
				} else if(flags.equals(flags2000) == false) {
					LOGGER.error("Validation of flags failed. DS has " + flags + " vs cache " + flags2000);
					System.exit(0);
				}
				mySets.add(e.tx);
			}
			// Second pass:
			// Check no flags in db but not on disk
			List<EclFlags> knownDb = crud.query(crud.buildQuery("SELECT * FROM flag WHERE siteId=?", siteId));
			for(EclFlags f : knownDb) {
				if(mySets.contains(f.getTxNo()) == false) {
					LOGGER.error("Validation of flags failed. Cached " + f + " not found on disk");
					System.exit(0);
				}
			}
		} catch(IOException | SQLException ee) {
			throw new CloudException(ee);
		}
		
	}
	
	public boolean versionExists(VmVersion vv) {
		try (Connection con = dao.getReadOnlyConnection()) {
			VmVersionCrud crud = new VmVersionCrud(con);
			ResultSet rs = crud.buildQuery("SELECT * FROM vversion vv, flag f WHERE uuid=? AND version=? AND f.txNo = vv.flagId AND f.state > 2 AND f.siteId=? AND vv.siteId=?", vv.getPlatformStyleUuid(), vv.getVersion(), siteId, siteId);
			if(rs.next()) {
				return true;
			}
			return false;
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	/**
	 * I'm an OO sinner, please save me
	 * 
	 */
	public synchronized Connection getReadConnection() {
		return dao.getReadOnlyConnection();
	}
	
	public VmVersion getVersionFromRestoreId(long restoreId, long timestamp) {
		try (Connection con = dao.getReadOnlyConnection()) {
			VmVersionCrud crud = new VmVersionCrud(con);
			String sql = "FROM vversion vv, uuid u, flag f WHERE u.uuid = vv.uuid AND f.txNo = vv.flagId AND f.state > 2 AND vv.siteId=? AND f.siteId=? AND u.id=? AND vv.version=?"; 
			ResultSet rs = crud.buildQuery("SELECT vv.* " + sql, siteId, siteId, restoreId, timestamp);
			ResultSet rs2 = crud.buildQuery("SELECT * FROM vversion_disk vd WHERE siteId=? AND versionId IN (SELECT vv.flagId " + sql + ")", siteId, siteId, siteId, restoreId, timestamp);
			return crud.querySingle(rs ,rs2);
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public List<Long> getRestoreIds(List<VmVersion> versions) {
		List<String> uuids = versions.stream().map(VmVersion::getPlatformStyleUuid).collect(Collectors.toList());
		try (Connection con = dao.getReadOnlyConnection()) {
			UuidPairCrud cruft = new UuidPairCrud(con);
			String sql = "SELECT * FROM uuid WHERE uuid=?";
			return uuids.stream().map(u -> cruft.querySingle(cruft.buildQuery(sql, u)).getId()).collect(Collectors.toList());
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public List<Long> getPurgeTransactions() {
		try (Connection con = dao.getReadOnlyConnection()) {
			FlagCrud crud = new FlagCrud(con);
			ResultSet rs = crud.buildQuery("SELECT * FROM flag WHERE siteId=? AND state = 2 AND deleteTx!=0", siteId);
			List<Long> guys = crud.query(rs).stream().map(EclFlags::getTxNo).collect(Collectors.toList());
			// Obtain rolled back transactions as well
			rs = crud.buildQuery("SELECT * FROM flag WHERE siteId=? AND state = 0", siteId);
			guys.addAll(crud.query(rs).stream().map(EclFlags::getTxNo).collect(Collectors.toList()));
			return guys;
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public synchronized void reconcilePurgeTransactions(List<Long> deleteTxs) {
		if(deleteTxs.size() == 0) {
			return;
		}
		try (Connection con = dao.getWriteConnection()) {
			FlagCrud crud = new FlagCrud(con);
			Set<Long> toDelete = new HashSet<Long>();
			for(Long shlong : deleteTxs) {
				EclFlags f = crud.querySingle(crud.buildQuery("SELECT * FROM flag WHERE siteId=? AND txNo=?", siteId, shlong));
				if(f == null) {
					continue;
				}
				if(f.getDeleteTx() != 0) {
					f.setState(TransactState.TransactReconned.getValue());
					updateEclFlag(f, con, false);
				} else {
					toDelete.add(shlong);
				}
			}
			deleteTxs(toDelete, con);
			dumpTransactFile(con);
			con.commit();
		} catch(SQLException|IOException e) {
			throw new CloudException(e);
		}
	}
	
	/**
	 * Returns all vaulted versions known to Alike
	 * @return
	 */
	public List<Long> listAllVersions() {
		try (Connection con = dao.getReadOnlyConnection()) {
			return allVersions(con).stream().map(EclFlags::getTxNo).collect(Collectors.toList());
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public  List<VmVersion> listUniqueVms() {
		try (Connection con = dao.getReadOnlyConnection()) {
			VmVersionCrud crud = new VmVersionCrud(con);
			String sql = "FROM vversion_vaulted_v vv WHERE vv.siteId=? GROUP BY vv.uuid ORDER by vv.uuid"; 
			ResultSet rs = crud.buildQuery("SELECT vv.* " + sql, siteId);
			ResultSet rs2 = crud.buildQuery("SELECT vd.* FROM vversion_disk_vaulted_v vd, vversion_vaulted_v vv " 
							+ "WHERE vv.flagId = vd.versionId AND vv.siteId = vd.siteId AND vv.siteId=? GROUP BY vv.uuid ORDER BY vv.uuid", siteId);
			return crud.query(rs ,rs2);
			
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public List<VmVersion> listVersionsFromUuid(String platformStyleUuid) {
		try (Connection con = dao.getReadOnlyConnection()) {
			VmVersionCrud crud = new VmVersionCrud(con);
			String sql = "FROM vversion_vaulted_v vv WHERE vv.siteId=? AND vv.uuid=?"; 
			ResultSet rs = crud.buildQuery("SELECT vv.* " + sql, siteId, platformStyleUuid);
			ResultSet rs2 = crud.buildQuery("SELECT * FROM vversion_disk_vaulted_v vd WHERE siteId=? AND versionId IN (SELECT flagId " + sql + ")", siteId, siteId, platformStyleUuid);
			return crud.query(rs ,rs2);
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public List<VmVersion> listVersionsFromRestoreId(long restoreId) {
		try (Connection con = dao.getReadOnlyConnection()) {
			VmVersionCrud crud = new VmVersionCrud(con);
			String sql = "FROM vversion_vaulted_v vv, uuid u WHERE u.uuid = vv.uuid AND vv.siteId=? AND u.id=?"; 
			ResultSet rs = crud.buildQuery("SELECT vv.* " + sql, siteId, restoreId);
			ResultSet rs2 = crud.buildQuery("SELECT * FROM vversion_disk_vaulted_v vd WHERE siteId=? AND versionId IN (SELECT flagId " + sql + ")", siteId, siteId, restoreId);
			return crud.query(rs ,rs2);
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	public synchronized List<EclFlags> getReconFlags(int batchSize) {
		List<EclFlags> good = null;
		try (Connection con = dao.getReadOnlyConnection()) {
			FlagCrud crud = new FlagCrud(con);
			return crud.query(crud.buildQuery("SELECT * FROM flag_unreconned_vw WHERE siteId=? LIMIT ?", siteId, batchSize));
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public synchronized void setReconSeqs(List<EclFlags> flags, int reconSeq) {
		try (Connection con = dao.getWriteConnection()) {
			for(EclFlags f : flags) {
				f.setReconSeq(reconSeq);
				updateEclFlag(f, con, false);
			}
			con.commit();
		} catch(SQLException e) {
			throw new CloudException(e);
		}
		verifyFlags();
			
	}
	
	public synchronized void resync(FlagConsumer callback, DoubleConsumer progress) {
		// We need to find all the transactions that are active but are not delete transactions.
		try (Connection con = dao.getReadOnlyConnection()) {
			FlagCrud crud = new FlagCrud(con);
			String myQuery = "from flag WHERE deleteTx = 0 AND state=? AND siteId=?";
			ResultSet rs = crud.buildQuery("SELECT count(*) " + myQuery, TransactState.TransactActive.getValue(), siteId);
			int count = 0;
			if(rs.next()) {
				count = rs.getInt(1);
			}
			rs = crud.buildQuery("SELECT * " + myQuery, TransactState.TransactActive.getValue(), siteId);
			int pos = 0;
			double prog = 0.001;
			while(rs.next()) {
				EclFlags flags = crud.toObject(rs);
				callback.accept(flags, prog);
				/* flags.setState(TransactState.TransactRollback.getValue());
				flags.setOwnerTs(System.currentTimeMillis());
				updateEclFlag(flags, con); */
				prog = ++pos / count * 100;
				progress.accept(prog);
			}
			progress.accept(100);
		} catch(SQLException e) {
			throw new CloudException(e);
		}
		verifyFlags();
	}
	
	
	public synchronized void rebuild(FlagConsumer callback, DoubleConsumer progress) {
		globalProgress = 0;
		try (Connection con = dao.getReadOnlyConnection()) {
			// obtain all active vaults
			List<EclFlags> goodVaults = allVersions(con);
			FlagCrud crud = new FlagCrud(con);
			// Add in un-reconciled delete txs
			List<EclFlags> abandonedVaults = crud.query(crud.buildQuery("SELECT * FROM flag WHERE siteId=? AND state=? AND deleteTx=0", siteId, TransactState.TransactActive.getValue()));
			List<EclFlags> deletes = crud.query(crud.buildQuery("SELECT * FROM flag WHERE siteId=? AND (state=2 OR state=0) AND deleteTx!=0", siteId));
			List<EclFlags> combo = new ArrayList<EclFlags>();
			combo.addAll(abandonedVaults);
			combo.addAll(goodVaults);
			combo.addAll(deletes);
			LOGGER.info("Rebuild will process " + goodVaults.size() + " vaulted, " + abandonedVaults.size() + " abandoned, and " + deletes.size() + " deleted vaults");
			
			int pos = 0;
			int count = combo.size();
			double prog = 0.0001;
			for(EclFlags flags : combo) {
				callback.accept(flags, prog);
				prog = ((double) ++pos) / ((double) count) * 100.00;
				progress.accept(prog);
				globalProgress = prog;
			}
			progress.accept(100);
			globalProgress = 100;
		} catch(SQLException e) {
			throw new CloudException(e);
		}
		verifyFlags();
	}
	
	
	
	public synchronized String getRemoteMd5() {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		String current = "";
		if(adp.stat("transact.tx") != false) {
			current = adp.id("transact.tx");
		}
		// Also fold in ods state
		String odsPath = VaultSettings.instance().getRemoteDbPath() + "/" + siteId + "ods.db";
		File f = new File(odsPath);
		String foo = "";
		if(f.exists()) {
			foo = "" + f.lastModified() + f.length(); 
		}
		foo += current;
		try {
			current = CryptUtil.makeMd5Hash(foo.getBytes("US-ASCII"));
		} catch (UnsupportedEncodingException e) {
			;
		}
		return current;
	}
	
	public synchronized boolean hasGoodMd5() {
		String txFile = getRemoteMd5();
		String dbState = getTransactMd5();
		
		LOGGER.debug("Current md5 of transact.tx file is: " + txFile + " vs db: " + dbState);
		return txFile.equals(dbState);
	}
	
	
	/**
	 * Checks the timestamp in our database against the timestamp
	 * @return
	 */
	public synchronized boolean checkTimestamp() {
		try (Connection con = dao.getReadOnlyConnection()) {
			FlagCrud fCrud = new FlagCrud(con);
			DsInfo info = makeDsInfo(con, true);
			EclFlags flags = fCrud.querySingle(fCrud.buildQuery("SELECT * FROM flag WHERE siteId=? ORDER BY ownerTs DESC LIMIT 1", siteId));
			// Nothing here, ergo our timestamp is fine
			if(flags == null) {
				return true;
			}
			if(info.getTimestamp() > flags.getOwnerTs()) {
				return true;
			}
			return false;
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public MaintStats getMaintStats() {
		try (Connection con = dao.getReadOnlyConnection()) {
			DsInfo info = makeDsInfo(con, true);
			return info.getMaintStats();
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public synchronized void setMaintStats(MaintStats stats) {
		try (Connection con = dao.getWriteConnection()) {
			DsInfoCrud crud = new DsInfoCrud(con);
			DsInfo info = makeDsInfo(con, true);
			info.setMaintStats(stats);
			crud.update(info);
			con.commit();
		} catch(SQLException e) {
			throw new CloudException(e);
		}	
		// Dump these stats out to JSON as well
		try {
			String result = new ObjectMapper().writeValueAsString(stats);
			try (FileWriter fw = new FileWriter("/tmp/metrics/" + siteId + "_maint.stats")) {
				fw.append(result);
			}
		} catch(IOException e) {
			throw new CloudException(e);
		}
		
	}
	
	public synchronized void setTransactMd5(String md5) {
		try (Connection con = dao.getWriteConnection()) {
			DsInfoCrud crud = new DsInfoCrud(con);
			crud.buildQuery("UPDATE dsinfo SET md5=? WHERE siteId=?", md5, siteId);
			con.commit();
			//LOGGER.debug("Setting MD5 to " + md5);
		} catch(SQLException e) {
			throw new CloudException(e);
		}	
	}
	
	public synchronized void setTimestamp(long time) {
		try (Connection con = dao.getWriteConnection()) {
			DsInfoCrud crud = new DsInfoCrud(con);
			crud.buildQuery("UPDATE dsinfo SET timestamp=? WHERE siteId=?", time, siteId);
			con.commit();
		} catch(SQLException e) {
			throw new CloudException(e);
		}	
	}
	
	/**
	 * Furnishes the current md5 of the transact.tx file.
	 * @return
	 */
	private synchronized String getTransactMd5() {
		// Determine our md5 state from the DB
		try (Connection con = dao.getReadOnlyConnection()) {
			DsInfo info = makeDsInfo(con, true);
			return info.getLastTransactMd5();
		} catch(SQLException e) {
			throw new CloudException(e);
		}	
	}
	
	public synchronized EclFlags createTxBegin() {
		EclFlags patriot = new EclFlags();
		try (Connection con = dao.getWriteConnection()) {
			FlagCrud crud = new FlagCrud(con);
			if(largestTx == 0) {
				String query = "SELECT * FROM flag WHERE siteId=? ORDER BY txNo DESC LIMIT 1";
				EclFlags flag = crud.querySingle(crud.buildQuery(query,siteId));	
				if(flag == null) {
					largestTx = 0;
				} else {
					largestTx = flag.getTxNo();
				}
			}
		
			largestTx++;
			patriot.setTxNo(largestTx);
			patriot.setState(TransactState.TransactBegin.getValue());
			patriot.setOwnerTs(System.currentTimeMillis());
			patriot.setSiteId(siteId);
			updateEclFlag(patriot, con, true);
			con.commit();
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}	
		verifyFlags();
		return patriot;
	}
	
	public synchronized void commitBegin(EclFlags flags) {
		long tx = flags.getTxNo();
		try (Connection con = dao.getWriteConnection()) {
			FlagCrud crud = new FlagCrud(con);
			EclFlags old = crud.querySingle(crud.buildQuery("SELECT * FROM flag WHERE txNo=? AND siteId=?", tx, siteId));
			if(old == null) {
				throw new CloudException("Transaction not found");
			}
			if(old.getState() != TransactState.TransactBegin.getValue()) {
				throw new CloudException("Found a transaction to create, but not in correct state");
			}
			// Force the state we want
			flags.setState(TransactState.TransactActive.getValue());

			flags.setOwnerTs(System.currentTimeMillis());
			updateEclFlag(flags, con, true);
			con.commit();
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}	
		verifyFlags();
	}
	
	
	/**
	 * Used to commit a vault transaction to the transact.tx system so its state becomes TransactVaulted
	 * @param tx the transaction in question
	 * @param flags updates to the vault's flags, if any
	 */
	public synchronized void commitVault(long tx, VmVersion version) {
		LOGGER.info("Header committing for tx " + tx);
		//CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		try (Connection con = dao.getWriteConnection()) {
			FlagCrud crud = new FlagCrud(con);
			EclFlags old = crud.querySingle(crud.buildQuery("SELECT * FROM flag WHERE txNo=? AND siteId=?", tx, siteId));
			if(old == null) {
				throw new CloudException("Key not found");
			}
			if(old.getState() != TransactState.TransactActive.getValue()) {
				throw new CloudException("Found a transaction to commit, but not in correct state");
			}
			// Update timestamp and state
			old.setOwnerTs(System.currentTimeMillis());
			HeaderEventType theEvent = HeaderEventType.changed;
			
			// Only non-delete transactions need to do this cruft
			old.setState(TransactState.TransactVaulted.getValue());
			storeMeta(con, version);
			updateEclFlag(old, con, true);

			// Note that once we've called updateEclFlag, the transact file for this vault is now TranactVaulted, so
			// if we except here, it's still "on the books" as vaulted
			con.commit();
			if(version != null) {
				fireVmChanged(version, theEvent);
			} else {
				// This is a problem, but it's logged early
			}
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
		verifyFlags();
	}
	
	public synchronized void commitDelete(EclFlags flags) {
		long tx = flags.getTxNo();
		try (Connection con = dao.getWriteConnection()) {
			FlagCrud crud = new FlagCrud(con);
			EclFlags old = crud.querySingle(crud.buildQuery("SELECT * FROM flag WHERE txNo=? AND siteId=?", tx, siteId));
			if(old == null) {
				throw new CloudException("Transaction not found");
			}
			// Do some sanity checking
			EclFlags entryToDelete = 
					crud.querySingle(crud.buildQuery("SELECT * FROM flag WHERE txNo=? AND siteId=?", flags.getDeleteTx(), siteId));
			if(entryToDelete == null) {
				throw new CloudException("Cannot finish delete transaction; no such vault " + flags.getDeleteTx() + " to delete exists");
			}
			if(entryToDelete.getState() < TransactState.TransactVaulted.getValue()) {
				throw new CloudException("Cannot delete transaction " + flags.getDeleteTx() + ", vault to delete is still in progress");
			}
			
			ResultSet rs = crud.buildQuery("SELECT * FROM vversion WHERE siteId=? AND flagId=?", siteId, entryToDelete.getTxNo());
			if(rs.next() == false) {
				LOGGER.error("Attempt to delete transaction " + entryToDelete.getTxNo() + " on site " + siteId + " but no version found for it!");
			}
			
			// Force the state we want--which is "active" for deletes!
			flags.setState(TransactState.TransactActive.getValue());
			
			flags.setOwnerTs(System.currentTimeMillis());
			updateEclFlag(flags, con, true);
			// Delete the metainformation of the deleted vault so it's no longer enumerated
			VmVersion version = killMetaForDeleteTx(con, flags.getDeleteTx());
			if(version != null) {
				fireVmChanged(version, HeaderEventType.deleted);
			}
			con.commit();
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
	}
	
	/**
	 * Loads and returns the flags associated with a vault tx.
	 * @param tx
	 * @return
	 */
	public synchronized EclFlags loadFlag(long tx) {
		try (Connection con = dao.getReadOnlyConnection()) {
			FlagCrud crud = new FlagCrud(con);
			EclFlags flags = crud.querySingle(crud.buildQuery("SELECT * FROM flag WHERE txNo=? AND siteId=?", tx, siteId));
			// Not found
			if(flags == null) {
				throw new CloudException("Transaction not found");
			}
			return flags;
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
		
	}
	
	/*private synchronized void saveFlag(EclFlags flags) {
		try (Connection con = dao.getWriteConnection()) {
			updateEclFlag(flags, con);
		} catch(SQLException sqle) { 
			throw new CloudException(sqle);
		}
	}*/
	
	public synchronized void reconCleanup(JobControl control) {

		LOGGER.debug("Entering reconCleanup");
		
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		try (LeasedLock cleanupLock = new LeasedLock(adp, "cleanup.lock", Ods.LOCK_DURATION_SECS /2)) {
			if(LeasedLock.isLocked(adp, "recon.lock", Ods.LOCK_DURATION_SECS)) {
				LOGGER.info("Cleanup waiting on reconciler to finish");
				while(LeasedLock.isLocked(adp, "recon.lock", Ods.LOCK_DURATION_SECS)) {
					try { Thread.sleep(1000); } catch(Throwable t) {;}
					control.control();
				}
			}
			try (Connection con = dao.getWriteConnection()) {
				FlagCrud crud = new FlagCrud(con);
				
				long olderThan = System.currentTimeMillis() - (BROKEN_TX_WINDOW_MINS * 1000 * 60);
				// Find broken txs--those that were never started
				ResultSet rs = crud.buildQuery("SELECT * FROM flag WHERE siteId=? AND ownerTs < ? AND state =? AND deleteTx=0", 
												siteId, olderThan, TransactState.TransactBegin.getValue());
				Set<Long> failed = new HashSet<Long>();
				List<EclFlags> flags = crud.query(rs);
				for(EclFlags ff : flags) {
					failed.add(ff.getTxNo());
					LOGGER.debug("Flag " + ff + " is too old!");
				}
				crud.query(rs).stream().forEach(e -> failed.add(e.getTxNo()));
				if(failed.size() == 0) {
					LOGGER.info("Found no abandoned transactions older than " + BROKEN_TX_WINDOW_MINS + " minutes to delete");
				} else {
					LOGGER.info("Found " + failed.size() + " abandoned transactions to delete");
				}
				// Now find deleted transactions
				Set<Long> toDelete = findDeletedPairs(con, crud);
				// Now delete them
				toDelete.addAll(failed);
				if(toDelete.size() == 0) {
					LOGGER.debug("Found no deleted vaults to clean up");
				} else {
					LOGGER.info("Found " + toDelete.size() + " deleted vaults to clean up");
				}
				deleteTxs(toDelete, con);
				con.commit();
			} catch(SQLException e) {
				throw new CloudException(e);
			} 
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		verifyFlags();
	}
	
	public synchronized void postSyncCleanup(JobControl control) {
		try (Connection con = dao.getWriteConnection()) {
			FlagCrud crud = new FlagCrud(con);
			List<EclFlags> toRollback = crud.query(crud.buildQuery("SELECT * FROM flag WHERE siteId=? AND state = 2 AND deleteTx=0", siteId));
			for(EclFlags f : toRollback) {
				f.setState(TransactState.TransactRollback.getValue());
				updateEclFlag(f, con, false);
			}
			if(toRollback.size() > 0) {
				LOGGER.info("Post-sync cleanup found " + toRollback.size() + " abandoned transactions that have been rolled back.");
				dumpTransactFile(con);
			}
			con.commit();
		} catch(SQLException e) {
			throw new CloudException(e);
		} 
	}
	
	private Set<Long> findDeletedPairs(Connection con, FlagCrud crud) {
		int reconned = TransactState.TransactReconned.getValue();
		//int vaulted = TransactState.TransactVaulted.getValue();
		
		Set<Long> vaults = new HashSet<Long>();
		Set<Long> deletes = new HashSet<Long>();
		// Find vaults whose txNo matches another transaction's deleteTx.
		// This vault must be in "vaulted" or "reconned" state, and its paired transaction must also be "reconned".
		ResultSet rs = crud.buildQuery("SELECT * FROM flag WHERE siteId=? AND state > 2 AND txNo in " 
				+ "(SELECT deleteTx FROM flag WHERE siteId=? AND state = 4)", siteId, siteId);
		crud.query(rs).stream().forEach(e -> vaults.add(e.getTxNo()));
		
		/* This code will later help us with dups?
		List<EclFlags> allDeletes = crud.query(rs);
		allDeletes.stream().forEach(e -> vaults.add(e.getTxNo()));
		allDeletes = allDeletes.stream().filter(distinctByKey(EclFlags::getDeleteTx)).collect(Collectors.toList());
		allDeletes.stream().forEach(e -> vaults.add(e.getTxNo()));
		*/
		// Find the delete transactions that are finished up
		// This transaction's deleteTx must match a "normal" transaction whose state is vaulted or reconned
		rs = crud.buildQuery("SELECT * FROM flag WHERE siteId=? AND state=4 AND deleteTx in " 
				+ "(SELECT txNo FROM flag WHERE siteId=? AND (state=3 OR state = 4))", siteId, siteId);
		crud.query(rs).stream().forEach(e -> deletes.add(e.getTxNo()));
		
		if(vaults.size() != deletes.size()) {
			LOGGER.error("Vaults and deletes are unmatched: " + deletes + " deletes vs " + vaults + " vaults.");
		}
		vaults.addAll(deletes);
		return vaults;
		
	}
	
	public synchronized int getVaultedReconSeq() {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		if(adp.stat("ods.db") == false) {
			return 0;
		}
		GetResult rez = adp.getBlock("ods.db", ODS_SEQ_OFFSET + 4);
		try (InputStream is = rez.in) {
			ByteBuffer buffy = ByteBuffer.allocate(4);
			// SQLite3 is big-endian internally
			buffy.order(ByteOrder.BIG_ENDIAN);
			is.skip(ODS_SEQ_OFFSET);
			if(is.read(buffy.array()) != buffy.capacity()) {
				throw new CloudException("Unable to read ods.db userseq due to premature EOF");
			}
			// user_seq is a signed int
			int inty = buffy.getInt();
			return inty;
		} catch(IOException e) {
			throw new CloudException(e);
		}
	}
	
	public synchronized VmVersion getMetaData(final String uuid2, long epoch) {
		// Physical systems have non-normalized GUIDs that don't have hyphens.
		// These simply don't match anything in our database as result.
		String uuid = uuid2;
		if(uuid.contains("-") == false) {
			uuid = VmVersion.unfixUuid(uuid);
		}
		try (Connection con = dao.getWriteConnection()) {
			VmVersionCrud crud = new VmVersionCrud(con);
			// Remember to check against FLAG to make sure this vault has actually been sent
			ResultSet rs = crud.buildQuery("SELECT * FROM vversion vv, flag f WHERE vv.uuid=? AND vv.version=? AND f.txNo = vv.flagId AND f.state > 2 AND f.siteId=?", uuid, epoch, siteId);
			ResultSet rs2 = crud.buildQuery(
							"SELECT * FROM vversion_disk vd WHERE siteId=? AND vd.versionId IN (SELECT vv.flagId FROM vversion vv WHERE vv.uuid=? AND vv.version=? AND vv.siteId=? AND vv.siteId=vd.siteId AND vv.flagId=vd.versionId)", 
							siteId, uuid, epoch, siteId);
			if(rs.next() && rs2.next()) {
				return crud.toObject(rs, rs2);
			}
			return null;
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public Dao exposeDao() {
		return this.dao;
	}
	
	public synchronized void fireVmEvent(VmVersion v, HeaderEvent he) {
		listeners.forEach(a -> {
			try {
				a.accept(he); 
			} catch(Throwable te) {
				LOGGER.error("Encountered error in event callback" , te);
			}
		});
	}
	
	private List<EclFlags> allVersions(Connection con) {
		FlagCrud crud = new FlagCrud(con);
		ResultSet rs = crud.buildQuery("SELECT * FROM flag WHERE siteId=? AND txNo in (SELECT flagId FROM vversion_vaulted_v WHERE siteId=?)", siteId, siteId);
		return crud.query(rs);
		
	}
	
	private void checkAndMakeDsEntry() {
		try (Connection con = dao.getWriteConnection()) {
			DsInfoCrud crud = new DsInfoCrud(con);
			//Map<String,String> sets = VaultSettings.instance().getSettings();
			DsInfo scrap = new DsInfo();
			scrap.setIdFromShare(VaultSettings.instance().getShareConfig(siteId));
			DsInfo inThere = makeDsInfo(con, false);
			if(inThere == null) {
				// Create an entry if it doesn't exist elswhere
				inThere = crud.querySingle(crud.buildQuery("SELECT * FROM dsinfo WHERE identifier=?", scrap.getIdentifier()));
				if(inThere != null) {
					throw new CloudException("Cannot create ODS connection to data store #" + siteId + " because it already exists at #" + inThere.getSiteId());
				}
				scrap.setSiteId(siteId);
				scrap.setLastTransactMd5("__UNSYNC");
				crud.create(scrap);
				con.commit();
			} else if(scrap.getIdentifier().equals(inThere.getIdentifier()) == false) {
				LOGGER.info("Data store settings for site " + siteId + " have changed since last connection.");
				inThere.setIdentifier(scrap.getIdentifier());
				// We need to update this.
				crud.update(inThere);
				con.commit();
			}
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
	
	
	private DsInfo makeDsInfo(Connection con, boolean except) {
		DsInfoCrud crud = new DsInfoCrud(con);
		DsInfo info = crud.querySingle(
					crud.buildQuery("SELECT * FROM dsinfo WHERE siteId=?", siteId),
					crud.buildQuery("SELECT count(*) FROM flag WHERE siteId=?", siteId),
					crud.buildQuery("SELECT count(*) FROM flag_unreconned_vw WHERE siteId=?", siteId)
				);
		if(info == null && except) {
			throw new CloudException("Site id " + siteId + " does not exist in database");
		}
		return info;
	}
	
	/**
	 *  Used during cleanup operations to purge dereferenced transactions of completed and reconciled txs
	 */
	private void deleteTxs(Set<Long> toDelete, Connection con) throws SQLException, IOException {
		// Always do this to recover from previous delete failures, making delete atomic
		loadAndRunDeleteLog(con);
		if(toDelete.size() == 0) {
			return;
		}
		FlagCrud crud = new FlagCrud(con);
		VmVersionCrud vCrud = new VmVersionCrud(con);
			
		List<VmVersion> listenerGuys = new ArrayList<VmVersion>();
		for(long tx : toDelete) {
			EclFlags flags = crud.querySingle(crud.buildQuery("SELECT * FROM FLAG WHERE txNo=? AND siteId=?", tx, siteId));
			if(flags != null) {
				crud.delete(flags);
				ResultSet r1 = vCrud.buildQuery("SELECT * FROM vversion WHERE flagId=? AND siteId=?", tx, siteId);
				ResultSet r2 = vCrud.buildQuery("SELECT * FROM vversion_disk WHERE versionId=? AND siteId=?" , tx, siteId);
				VmVersion version = null;
				if(r1.next() && r2.next()) {
					version = vCrud.toObject(r1, r2);
				}
				if(version != null) {
					// Only some transactions have metainformation. That's ok
					vCrud.delete(version);
					listenerGuys.add(version);
				} 
			} else {
				LOGGER.debug( "Asked to delete a tx that doesn't exist; probably it's a deleteTx that was cleaned out earlier");
			}
		}
		// We may orphan some data if this below fails, but we won't corrupt
		createDeleteLog(toDelete);
		loadAndRunDeleteLog(con);
		// Update interested parties
		fireVmsChanged(listenerGuys, HeaderEventType.deleted);
		
	}
	
	private synchronized void updateEclFlag(EclFlags flags, Connection con, boolean dumpTxFile) throws SQLException {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		if(adp.isReadOnly()) {
			throw new CloudException("Cannot update flag for site " + siteId + ", data store is READ-ONLY");
		}
		FlagCrud crud = new FlagCrud(con);
		EclFlags old = crud.querySingle(crud.buildQuery("SELECT * FROM flag WHERE txNo=? AND siteId=?", flags.getTxNo(), siteId));
		//boolean stateChanged = false;
		if(old != null && old.equals(flags)) {
			LOGGER.trace("Flags are identical; no need to update");
			return;
		} else {
			LOGGER.debug( "Updating flag from " + old + " to " + flags);
		}
		if(old == null) {
			crud.create(flags);
			old = flags;
		}
		
		// We don't need to write a flags file until transactBegin has finished,
		// which is an update
		if(old.flagsChangedAny(flags) ) {
			// Object changed, update DB
			crud.update(flags);
			if(old.flagsChangedNoState(flags)) {
				// Kick out the changes to file
				VaultUtil.putBlockFromMem(flags.store(), flags.recordSize(), adp, "" + flags.getTxNo() + ".flags");
			}
		}
		if(dumpTxFile) {
			dumpTransactFile(con);
		}
	}
	
	private void dumpTransactFile(Connection con) throws SQLException {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		if(adp.isReadOnly()) {
			throw new CloudException("Cannot dump transactive file for site " + siteId + ", data store is READ-ONLY");
		}
		// Back up the file first
		if(txBackupTimer.getElapsed(TimeUnit.MINUTES) >= 60) {
			backupTxFileIfValid(adp);
			txBackupTimer.reset();
		}
		FlagCrud crud = new FlagCrud(con);
		ResultSet rs = crud.buildQuery("SELECT count(*) FROM FLAG WHERE siteId=?", siteId);
		int cnt = 0;
		if(rs.next()) {
			cnt = rs.getInt(1);
		}
		Pair<OutputStream, MessageDigest> nicePair = VaultUtil.putBlockFromFile(adp,"transact.tx", cnt * (TxEntry.MY_SIZE +1));
		String myMd5 = dumpTransactFile(nicePair.first, con);
		if(CryptUtil.bytesToHex(nicePair.second.digest()).equals(myMd5)) {
			throw new CloudException("MD5 of sent transact.tx does not match payload");
		}
		/* DsInfoCrud crud = new DsInfoCrud(con);
		DsInfo info = makeDsInfo(con, true);
		info.setLastTransactMd5(myMd5);
		crud.update(info);*/
	}
	
	
	/**
	 * Dumps the transact.tx file out
	 * @param myFileStream the stream to dump to
	 * @return an md5 hex hash of the stream
	 */
	private String dumpTransactFile(OutputStream myFileStream, Connection con) throws SQLException {
		
		LOGGER.debug( "Storing transact.tx file");
		FlagCrud crud = new FlagCrud(con);
		ResultSet rs = crud.buildQuery("SELECT * FROM FLAG WHERE siteId=? ORDER BY txNo", siteId);
		
		try (DigestOutputStream shotput = CryptUtil.getRollingMd5(myFileStream)) {
			// Write out the version info
			String versionString = CryptUtil.intToHexMsStyle(TRANSACT_TX_FILE_VERSION);
			shotput.write(versionString.getBytes("US-ASCII"));
			shotput.write((byte) '\n');
			
			while(rs.next()) {
				EclFlags flagger = crud.toObject(rs);
				TxEntry entry = new TxEntry(flagger);
				shotput.write(entry.store());
			}
			return CryptUtil.bytesToHex(shotput.getMessageDigest().digest());
		} catch(SQLException|IOException e) {
			throw new CloudException(e);
		}
		
	}
	
	private void backupTxFileIfValid(CloudAdapter adp) {
		String lockFile = "/tmp/tx-locker" + siteId;
		try (Flocker f = new Flocker()){
			// This will throw if bad
			if(adp.stat("transact.tx") == false) {
					LOGGER.debug("Transact.tx file is blank or empty");
			}
			// Verify MD5
			String fiver = adp.id("transact.tx");
			String sourcePath = "/mnt/ads/transact.tx";
			if(this.siteId == 1) {
				sourcePath = "/mnt/ods1/transact.tx";
			}
			String destPath = sourcePath + ".bak";
			String rez = VaultUtil.ezExec("cp -f $1 $2", sourcePath, destPath);
			if(rez.trim().isEmpty() == false) {
				throw new CloudException("Backup of transact.tx file failed with " + rez);
			}
			String fiver2 = adp.id("transact.tx.bak");
			if(fiver2.equals(fiver) == false) {
				throw new CloudException("Backup of transact.tx file for site " + siteId + " failed--backup MD5 is different than original!");
			}
			LOGGER.info("Backup of transact.tx for site " + siteId + " successful");
		} catch(CloudException cex) {
			LOGGER.error("Unable to back up transact.tx file", cex);
		} catch(IOException ieo) {
			LOGGER.error("Unable to back up transact.tx file", ieo);
		}
	}
	
	private CloseableIterator<TxEntry> loadTransactFile() {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		GetResult rez = VaultUtil.getBlockViaFile(adp, "transact.tx");
		FileInputStream is = (FileInputStream) rez.in;
		try {
			return loadTransactFile(is.getChannel());
		} catch (IOException e) {
			throw new CloudException(e);
		}
	}
	
	/**
	 * Uses BinarySearch to find transactions 
	 * @param startTx first tx to load, or -1 if you want to start at the very beginning
	 */
	private CloseableIterator<TxEntry> loadTransactFile(FileChannel ch) throws IOException {
		LOGGER.debug( "Loading transact.tx file");
		String versionString = CryptUtil.intToHexMsStyle(TRANSACT_TX_FILE_VERSION);
		int skipBytes = versionString.length() +1; // Include space for a newline
		int entryCount = (int) (ch.size() - skipBytes) / TxEntry.MY_SIZE;
		
		ch.position(skipBytes);
		// Deal with final nonsense for anon classes
		
		
		return new CloseableIterator<TxEntry>() {
			private TxEntry next = null;
			private boolean readToEnd = false;
			
			@Override
			public boolean hasNext() {
				try {
					return loadNext();
				} catch(Exception e) {
					throw new CloudException(e);
				}
			}

			@Override
			public TxEntry next() {
				try {
					loadNext();
				} catch (IOException e) {
					throw new CloudException(e);
				}
				if(next == null) {
					throw new IllegalStateException("Iterator at end");
				}
				TxEntry toReturn = next;
				next = null;
				return toReturn;
			}
			
			private boolean loadNext() throws IOException {
				if(next == null && readToEnd == false) {
					byte [] bites = VaultUtil.ezLoad(ch, TxEntry.MY_SIZE);
					if(bites == null) {
						readToEnd = true;
						return false;
					}
					next = new TxEntry();
					next.load(bites);
				}
				if(next != null) {
					return true;
				} 
				return false;
			}

			@Override
			public void close() throws IOException {
				ch.close();
			}

			@Override
			public int count() {
				return entryCount;
			}		
		};		
	}
	
	
	private void loadAndRunDeleteLog(Connection con) throws IOException, SQLException {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		if(adp.stat("delete.tx") == false) {
			return;
		}
		Set<Long> toDelete = new HashSet<Long>();
		GetResult rez = adp.getBlock("delete.tx", 0);
		try (InputStream is = rez.in) {
			if(rez.len > (1024 * 1024 * 4)) {
				throw new CloudException("Delete log is unreasonably large");
			}
		
			byte [] suckIt = new byte[(int) rez.len];
			VaultUtil.toBytes(rez, suckIt);
			ByteBuffer buffy = ByteBuffer.wrap(suckIt);
			buffy.order(ByteOrder.LITTLE_ENDIAN);
			while(buffy.hasRemaining()) {
				toDelete.add(buffy.getLong());
			}
		}
		LOGGER.debug( "Found delete log with " + toDelete.size() + " transaction to process");
		for(Long tx : toDelete) {
			String flagsName = "" + tx + ".flags";
			String eclName = "" + tx + ".ecl";
			if(adp.stat(eclName)) {
				adp.del(eclName);
			}
			if(adp.stat(flagsName)) {
				adp.del(flagsName);
			}
		}
		// Okay, done here
		dumpTransactFile(con);
		adp.del("delete.tx");
		LOGGER.debug( "Deleted " + toDelete + " transactions from delete log successfully");
	}
	
	private void createDeleteLog(Set<Long> toDelete) {
		if(toDelete.size() == 0) {
			LOGGER.trace("No need to create delete log, returning");
			return;
		}
		LOGGER.debug( "Creating delete log for " + toDelete.size() + " transactions");
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		int sz = toDelete.size() * 8;
		ByteBuffer buffy = ByteBuffer.allocate(sz);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		for(long tx : toDelete) {
			buffy.putLong(tx);
		}
		VaultUtil.putBlockFromMem(buffy.array(), sz, adp, "delete.tx");
		LOGGER.info("Successfully uploaded delete log for " + toDelete.size() + " transactions");
	}
	
	private VmVersion loadAndStoreMeta(EclFlags flag, Connection con) throws Exception {
		CloudAdapter adp = VaultSettings.instance().getAdapter(siteId);
		String eclName = flag.getTxNo() + ".ecl";
		if(adp.stat(eclName) == false) {
			// Nada e pues nada
			return null;
		}
		PathedGetResult rez = VaultUtil.getBlockViaFile(adp, eclName);
		try (InputStream is = (rez.in)) {
			EclReader ecl = new EclReader(rez.localPath);
			VmVersion temp = ecl.toVmVersion(flag);
			storeMeta(con, temp);
			return temp;
		}
	}
	
	private void storeMeta(Connection con, VmVersion v) {
		VmVersionCrud crud = new VmVersionCrud(con);
		crud.create(v);
	}
	
	/**
	 * Kills vversion information of a vault who has been deleted by the TX passed
	 */
	private VmVersion killMetaForDeleteTx(Connection con, long deleteTx) throws SQLException {
		VmVersionCrud crud = new VmVersionCrud(con);
		
		ResultSet rs = crud.buildQuery("SELECT * FROM vversion WHERE flagId=? AND siteId=?", deleteTx, siteId);
		ResultSet rs2 = crud.buildQuery("SELECT * FROM vversion_disk vd WHERE versionId=? AND siteId=?", deleteTx, siteId);
		VmVersion vvv = null;
		if(rs.next() && rs2.next()) {
			 vvv = crud.toObject(rs, rs2);
		} else {
			LOGGER.error("Cannot clean version metainfo for tx " + deleteTx + " for site " + siteId + " because no metainfo found");
			return null;
		}
		crud.delete(vvv);
		return vvv;
		
	}
	
	
	private void fireVmsChanged(List<VmVersion> awesome, HeaderEventType t) {
		awesome.forEach(a -> fireVmChanged(a, t));
	}
	
	private void fireVmChanged(VmVersion awesome, HeaderEventType t) {
		HeaderEvent he = new HeaderEvent();
		he.type = t;
		he.version = awesome;
		listeners.forEach(a -> {
			try {
				a.accept(he); 
			} catch(Throwable te) {
				LOGGER.error("Encountered error in event callback" , te);
			}
		});
	}
	
	private EclFlags materializeFromOds(TxEntry e, CloudAdapter adp) throws IOException {
		EclFlags flags = new EclFlags();
		flags.setTxNo(e.tx);
		flags.setState(e.state);
		flags.setSiteId(siteId);
		String sixFlagsDeathCoaster = "" + e.tx + ".flags";
		if(adp.stat(sixFlagsDeathCoaster)) {
			GetResult gs = adp.getBlock(sixFlagsDeathCoaster, 0);
			try (InputStream is = gs.in) {
				VaultUtil.toByteStruct(gs, flags);
			}
			if(flags.getOwnerTs() == -1) {
				String msg = "Transaction " + flags + " is in an invalid state--bad timestamp";
				if(paranoidFlags) {
					throw new CloudException(msg);
				} else {
					LOGGER.error(msg);
					return null;
				}
			}
		} else {
			flags = new EclFlags();
			flags.setTxNo(e.tx);
			flags.setState(e.state);
			flags.setSiteId(siteId);
			flags.setOwnerTs(-1);
			if(flags.getState() != TransactState.TransactBegin.getValue()) {
				String msg = "Transaction " + e.tx + " is in an invalid state";
				if(paranoidFlags) {
					throw new CloudException(msg);
				} else {
					LOGGER.error(msg);
					return null;
				}
			}
		}
		return flags;
	}
	
}

/////////////////////////////////////////////////////////////////// Begin TxEntry

/**
 * Represents an entry in the transact.tx file
 *
 */
class TxEntry implements ByteStruct<TxEntry> {
	static final int MY_SIZE = (2 + 16 + 1 + 1 +1);
	long tx;
	int state;
	
	public TxEntry() {
		;
	}
	
	public TxEntry(EclFlags flagger) {
		this.tx = flagger.getTxNo();
		this.state = flagger.getState();
	}

	public EclFlags toFlags() {
		EclFlags flags = new EclFlags();
		flags.setState(state);
		flags.setDeleteTx(tx);
		return flags;
	}
	
	@Override
	public void load(byte[] bites) {
		if(bites.length < MY_SIZE) {
			throw new CloudException("Buffer too small");
		}
		
		String awesome = "";
		try {
			awesome = new String(bites, 0, 18, "US-ASCII");
		} catch(Exception e) {
			throw new CloudException(e);
		}
		tx = CryptUtil.hexToLong(awesome);
		char stately = (char) bites[19];
		state = Integer.parseInt("" + stately);
		
	}

	@Override
	public byte[] store() {
		String hex = CryptUtil.longToHex(tx);
		ByteBuffer buffy = ByteBuffer.allocate(MY_SIZE);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		try {
			buffy.put(hex.getBytes("US-ASCII"));
		} catch(Exception e) {
			throw new CloudException(e);
		}
		buffy.put((byte) ' ');
		String stateString = "" + state;
		buffy.put((byte) stateString.charAt(0));
		buffy.put((byte) '\n');
		return buffy.array();
	}

	@Override
	public int compareTo(TxEntry o) {
		return new Long(this.tx).compareTo(o.tx);
	}

	@Override
	public int recordSize() {
		return MY_SIZE;
	}
	
}
