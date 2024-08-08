package quadric.ods;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.VaultSettings;
import quadric.ods.dao.Crud;
import quadric.ods.dao.Dao;
import quadric.ods.dao.DaoFactory;
import quadric.ods.dao.PrintCrud;
import quadric.restore.BadBlockManager;
import quadric.stats.StatsManager;
import quadric.util.HclCursor;
import quadric.util.HclWriterUtil;
import quadric.util.Pair;
import quadric.util.Print;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;


/**
 * Represents a connection to an ods.db and is used for HCL journaling operations
 *
 */
public class JournalManager {
	private static final Logger LOGGER = LoggerFactory.getLogger( JournalManager.class.getName() );
	
	public enum Action {
		Incriment,
		Decrement,
		Clear;
	}
	
	private Dao dao;
	private long refCount = -1;
	private long blockCount = -1;
	private int siteId;
	private boolean useWall = false;
	
	public JournalManager() {
		;
	}
	
	
	public JournalManager(int siteId, String dbLocation) {
		init(siteId, dbLocation);
	}
	
	public void init(int siteId, String dbLocation) {
		this.siteId = siteId;
		int journalCommitInterval = VaultSettings.instance().getJournalCommitInterval();
		useWall = VaultSettings.instance().getJournalConnectionUseWall();
		List<Crud<?>> crudz = new ArrayList<Crud<?>>();
		crudz.add(new PrintCrud());
		try {
			
			
			dao = DaoFactory.instance().oneOff(dbLocation, crudz, useWall, false);
			updateRefCounts();
		} catch(CloudException ce) {
			if(ce.getMessage().contains("CORRUPT")) {
				LOGGER.warn("Database file at " + dbLocation + " is corrupted, will rebuild");
				new File(dbLocation).delete();
				dao = DaoFactory.instance().oneOff(dbLocation, crudz, useWall, false);
				updateRefCounts();
			} else {
				throw ce;
			}
		}
		StatsManager.instance().register("" + siteId + ".blockCount", () -> getRefCounts().first);
		StatsManager.instance().register("" + siteId + ".refCount", () -> getRefCounts().second);
		LOGGER.info("JournalManager initializing for site " + siteId + " with journal commit interval of " + journalCommitInterval);
	}
	
	/**
	 * Supplants the current ODS and replaces it with this one
	 * @param dest the offsite location 
	 * @param dbLocation the local ODS location
	 * @param progress for progress updates of the download
	 */
	public synchronized void downloadFromOffsite(CloudAdapter dest, String dbLocation, DoubleConsumer progress) {
		LOGGER.info("Downloading DS to local for site " + siteId + ", this may take a moment...");
		// Shut that cruft down
		if(dao != null) {
			dao.close();
			dao = null;
		}
		File f = new File(dbLocation);
		if(f.exists()) {
			boolean ok = f.delete(); 
			if(ok == false) {
				throw new CloudException("Unable to delete existing ods.db at location " + dbLocation);
			}
		}
		GetResult rez = dest.getBlock("ods.db", 0);
		byte [] bites = new byte[4096];
		try (InputStream is = rez.in; OutputStream fis = new BufferedOutputStream(new FileOutputStream(dbLocation))) {
			int tot = 0;
			while(true) {
				int amt = is.read(bites);
				if(amt == -1) break;
				fis.write(bites, 0, amt);
				tot += amt;
				double prog = ((double) tot) / rez.len * 100.00;
				progress.accept(prog);
			}
		} catch(IOException e) {
			throw new CloudException(e);
		}
		// Reset the dao
		List<Crud<?>> crudz = new ArrayList<Crud<?>>();
		crudz.add(new PrintCrud());
		dao = DaoFactory.instance().oneOff(dbLocation, crudz, useWall, false);
		updateRefCounts();
		progress.accept(100);
	}
	
	
	public synchronized void setReconSeq(int reconSeq) {
		try (Connection con = dao.getWriteConnection()) {
			PrintCrud crud = new PrintCrud(con);
			crud.buildTempQuery("PRAGMA user_version=" + reconSeq);
			con.commit();
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
	}
	
	public synchronized void dropAllData() {
		try (Connection con = dao.getWriteConnection()) {
			PrintCrud crud = new PrintCrud(con);
			crud.buildQuery("DELETE FROM prints");
			con.commit();
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
		updateRefCounts();
	}
	
	public void close() {
		if(dao != null) {
			dao.close();
		}
	}
	
	public Pair<Long,Long> getRefCounts() {
		return new Pair<Long,Long>(blockCount, refCount);
	}
	
	
	/**
	 * Creates a vault log representing all prints unknown to the vaulter system at this time. Uses a temporary
	 * table to avoid consuming too much memory.
	 * @param cursor the ECL of the original
	 * @param writers distribute prints amongst these writers
	 * @param progress updates on progress
	 */
	public synchronized void createVaultLog(HclCursor cursor, List<HclWriterUtil> writers, DoubleConsumer progress) {
		LOGGER.debug( "Creating NCL delta for journal with " + cursor.count() + " total prints");
		int journalCommitInterval = VaultSettings.instance().getJournalCommitInterval();
		try (Connection con = dao.getWriteConnection()) {
			PrintCrud crud = new PrintCrud(con);
			int pos = 0;
			DoubleConsumer nested = VaultUtil.nestedProgress(progress, 0, 50);
			long t1 = System.nanoTime();
			crud.buildQuery("DELETE FROM prints_tmp");
			crud.setBatchMode(true);
			boolean hasOutstandingCommit = false;
			while(cursor.hasNext()) {
				Print p = cursor.next();
				crud.buildQuery("INSERT OR REPLACE INTO prints_tmp " + " VALUES (?)", p.bytes);
				pos++;
				double goodTimes = ((double) pos / cursor.count()) *100.00;
				nested.accept(goodTimes);
				//insane.add(p);
				hasOutstandingCommit = true;
				if(pos % journalCommitInterval == 0) {
					doPeriodicCommit(crud, con, t1, journalCommitInterval);
					t1 = System.nanoTime();
					hasOutstandingCommit = false;
				}
			}
			// One last one...
			if(hasOutstandingCommit) {
				doPeriodicCommit(crud, con, t1, journalCommitInterval);
			}
			// Disable batch mode here on out
			crud.setBatchMode(false);
			
			
			LOGGER.debug( "All prints inserted into temporary land, now it's time to diff them...");
			//String query1 = "SELECT print, hex(print) AS hexxy FROM prints_tmp EXCEPT SELECT print, hex(print) AS hexxy FROM prints WHERE refCount > 0 ORDER BY hexxy";
			String query1 = "select prints_tmp.print, hex(prints_tmp.print) as hexxy  from prints_tmp left join prints on prints.print = prints_tmp.print where prints.refcount <= 0 or prints.print is null order by hexxy";
			String query2 = "SELECT count(*) from (" + query1 + ")";
			ResultSet rs = crud.buildTempQuery(query2);
			int count = 0;
			if(rs.next()) {
				count = rs.getInt(1);
			}
			if(count == 0) {
				LOGGER.debug( "No prints to send for this journal.");
			} else {
				LOGGER.debug( "Journal has " + count + " unique delta prints to send");
			}
			
			// It seems like any query on a temp table needs to be forcibly killed
			try (ResultSet rs2 = crud.buildTempQuery(query1)) {
				pos = 0;
				nested = VaultUtil.nestedProgress(progress, 50, 50);
				while(rs2.next()) {
					// This unique print is needed
					int writerNum = 0;
					// Avoid div by zero
					if(writers.size() > 1) {
						writerNum = pos % ( writers.size() -1);
					}
					Print p = new Print(rs2.getBytes(1));
					
					writers.get(writerNum +1).writePrint(p);
					writers.get(0).writePrint(p);
					//LOGGER.trace("Added print " + p + " to NCL");
					pos++;
					// Other half of the progress
					double prog = ((double) pos / count) * 100.00;
					nested.accept(prog);
				}
				LOGGER.trace("All prints saved to ncl..ready2rock!");
			}
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		} finally {
			// Always drop this crap
			try (Connection con = dao.getWriteConnection()) {
				PrintCrud crud = new PrintCrud(con);
				crud.buildQuery("DELETE FROM prints_tmp");
				con.commit();
			} catch(SQLException sqle) {
				throw new CloudException(sqle);
			}
		}
		for(HclWriterUtil writer : writers) {
			writer.writeCoda();
		}
		//updateRefCounts();
		progress.accept(100);
		
	}
	
	/**
	 * Applies a journal to the ods.db
	 * @param cursor the journal to apply
	 * @param jackson the action to apply
	 * @param progress a callback for progress updates
	 */
	public synchronized void applyJournal(HclCursor cursor, Action jackson, DoubleConsumer progress, String debugName) {
		LOGGER.debug("Applying journal action " + jackson.name() + " for journal " + debugName + " with " + cursor.count() + " prints to " + dao.getDbPath());
		int journalCommitInterval = VaultSettings.instance().getJournalCommitInterval();
		// If we barfed out of this last time, fix it this time
		if(refCount == -1) {
			updateRefCounts();
		}
		long t1 = System.nanoTime();
		try (Connection con = dao.getWriteConnection()) {
			PrintCrud crud = new PrintCrud(con);
			crud.setBatchMode(true);
			int pos = 0;
			boolean hasCommitted = false;
			while(cursor.hasNext()) {
				Print p = cursor.next();
				processPrint(p, jackson, crud);
				pos++;
				double rockin = ((double) pos) / cursor.count() * 100.00;
				progress.accept(rockin);
				hasCommitted = false;
				if(pos % journalCommitInterval == 0) {
					LOGGER.trace("Periodic commit of ods.db");
					crud.executeBatch();
					con.commit();
					hasCommitted = true;
				}
			}
			if(hasCommitted == false) {
				crud.executeBatch();
				con.commit();
			}
			long t2 = System.nanoTime();
			double ms = ((double) t2 - t1) / (1000000.00D);
			double throughput = ((double) cursor.count()) / ms;
			LOGGER.debug( "Journals applied in " + String.format("%.2f", throughput) + " prints/ms");
			progress.accept(100);
			
		} catch(SQLException sqle) {
			refCount = -1;
			throw new CloudException(sqle);
		}
		long old = refCount;
		if(jackson == Action.Decrement) {
			refCount -= cursor.count();
		} else if(jackson == Action.Incriment) {
			refCount += cursor.count();
		}
		updateRefCounts();
		int cnty = cursor.count(); 
		if(jackson == Action.Decrement) {
			old -= cnty;
		} else if(jackson == Action.Incriment) {
			old += cnty;
		} 
		if(old != refCount || refCount < 0) {
			LOGGER.error("Refcount issue, old was " + old + " vs new " + refCount);
			if(VaultSettings.instance().isParanoid()) {
				Runtime.getRuntime().exit(0);
			}
		}
	}
	
	public synchronized Set<Print> getDeletePrints(Set<Print> conflicts, int maintPurgeBatchSize) {
		try (Connection con = dao.getWriteConnection()) {
			PrintCrud crud = new PrintCrud(con);
			int theMax = maintPurgeBatchSize + conflicts.size();
			List<PrintRef> printz = crud.query(crud.buildQuery("SELECT * FROM prints WHERE refCount=0 LIMIT ?", theMax));
			// Filter out the baddies
			Set<Print> returnMe = printz.stream().filter(pr -> conflicts.contains(pr) == false).collect(Collectors.toSet());
			return returnMe;
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
	}
	
	/**
	 * Finish a purge operation by deleting purged prints from the ods.db
	 * @param toDelete
	 */
	public synchronized void finishPurge(Set<Print> toDelete) {
		if(refCount == -1) {
			updateRefCounts();
		}
		try (Connection con = dao.getWriteConnection()) {
			PrintCrud crud = new PrintCrud(con);
			crud.setBatchMode(true);
			for(Print p : toDelete) {
				crud.delete(new PrintRef(p));
			}
			crud.executeBatch();
			con.commit();
		} catch(SQLException sqle) {
			refCount = -1;
			throw new CloudException(sqle);
		}
		long before = refCount;
		updateRefCounts();
		if(before != refCount || refCount < 0) {
			LOGGER.error("Refcount issue, old was " + before + " vs new " + refCount);
			if(VaultSettings.instance().isParanoid()) {
				Runtime.getRuntime().exit(0);
			}
		}
		
	}
	
	public synchronized int deletePrintCount() {
		try (Connection con = dao.getWriteConnection()) {
			PrintCrud crud = new PrintCrud(con);
			int count = 0;
			ResultSet rs = crud.buildQuery("SELECT COUNT(*) FROM prints WHERE refCount=0");
			if(rs.next()) {
				count = rs.getInt(1);
			}
			return count;
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
	}
	
	/**
	 * Drops zero-ref prints
	 */
	public synchronized void dropZeroRefPrints(HclCursor c) {
		if(refCount == -1) {
			updateRefCounts();
		}
		try (Connection con = dao.getWriteConnection()) {
			PrintCrud crud = new PrintCrud(con);
			while(c.hasNext()) {
				crud.buildQuery("DELETE FROM prints WHERE refCount=0 AND print=?", c.next().bytes);
			}
			con.commit();
		} catch(SQLException fe) {
			refCount = -1;
			throw new CloudException(fe);
		} 
		long before = refCount;
		updateRefCounts();
		if(before != refCount || refCount < 0) {
			LOGGER.error("Refcount issue, old was " + before + " vs new " + refCount);
			if(VaultSettings.instance().isParanoid()) {
				Runtime.getRuntime().exit(0);
			}
		}
		
	}
	
	private void updateRefCounts() {
		try (Connection con = dao.getWriteConnection()) {
			PrintCrud crud = new PrintCrud(con);
			
			ResultSet rs = crud.buildQuery("SELECT count(rowid) FROM prints WHERE refCount > 0");
			if(rs.next()) {
				blockCount = rs.getLong(1);
			}
			if(refCount == -1 || VaultSettings.instance().isParanoid() == true) {
				LOGGER.info("Recalculating refCount from journaling database, please wait...");
				Stopwatch watchy = new Stopwatch();
				rs = crud.buildQuery("SELECT sum(refCount) FROM prints");
				LOGGER.debug("Recalcuated journaling refcount in " + watchy.getElapsed(TimeUnit.SECONDS) + " seconds");
				if(rs.next()) {
					refCount = rs.getLong(1);
				}
			}
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}	
	}
	
	private void doPeriodicCommit(PrintCrud crud, Connection con, long t1, int journalCommitInterval) throws SQLException {
		long t2 = System.nanoTime();
		double ms = ((double) t2 - t1) / (1000000.00D);
		double throughput = ((double) journalCommitInterval) / ms;
		crud.executeBatch();
		con.commit();
		LOGGER.trace("Periodic journal commit at " + String.format("%.2f", throughput) + " prints/ms");
	}
	
	private void processPrint(Print p, Action jackson, PrintCrud crud) throws SQLException {
		if(jackson == Action.Clear) {
			// Make sure the entry exists, but if it already exists, no worries
			crud.buildQuery("INSERT OR IGNORE INTO prints "
					+ ""
					+ ""
					+ "VALUES (?,0,0)", p.bytes);
		} else if(jackson == Action.Incriment) {
			String increment = "INSERT OR REPLACE INTO prints VALUES (?, (SELECT p.refCount FROM prints p WHERE print=? "
					+ "UNION SELECT 0 ORDER BY refCount DESC LIMIT 1) +1, 0)";
			crud.buildQuery(increment, p.store(), p.store());
		} else {
			String decrement = "INSERT OR REPLACE INTO prints VALUES (?, (SELECT p.refCount FROM prints p WHERE print=? "
					+ "UNION SELECT 0 ORDER BY refCount DESC LIMIT 1) -1, 0)";
			crud.buildQuery(decrement, p.store(), p.store());
		}
	}

}
