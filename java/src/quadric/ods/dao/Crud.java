package quadric.ods.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.util.HclWriterUtil;
import quadric.util.Stopwatch;

public abstract class Crud<T> {
	public static final int RETRY_COUNT = 100;
	public static final long RETRY_SLEEP_TIME_MS = 500;
	
	private static final Logger LOGGER = LoggerFactory.getLogger( Crud.class.getName() );
	
	protected Connection con = null;
	protected long lastInsertId;
	protected boolean batchMode = false;
	protected Statement batchStatement = null;

	private String lastQuery = "";
	private boolean hasLoggedDup = false;
	
	@FunctionalInterface
	public interface CheckedRunnable {
		void run() throws SQLException;
	}
	
	
	
	public Crud() {
		;
		
	}
	
	public void setBatchMode(boolean batchMode) {
		this.batchMode = batchMode;
		if(batchMode == false) {
			batchStatement = null;
		}
	}
	
	public int [] executeBatch() {
		if(batchStatement == null) {
			// No batch was ever started..
			LOGGER.trace("No transaction was ever started...");
			return new int[0];
		}
		try {
			return batchStatement.executeBatch();
		} catch(SQLException sqle) { 
			throw new CloudException(sqle);
		}
	}
	
	
	public long getLastInsertId() {
		return lastInsertId;
	}
	
	public Crud(Connection con) {
		setConnection(con);
	}
	
	public void setConnection(Connection con) {
		this.con = con;
	}
	
	public ResultSet buildTempQuery(String s) {
		try {
			Statement state = con.createStatement();
			boolean hasResults = state.execute(s);
			if(hasResults) {
				return state.getResultSet();
			}
			return null;
		} catch (SQLException e) {
			throw new CloudException("Unable to execute temp query " + s, e);
		}
	}
	
	public ResultSet buildQuery(String s, Object...params) {
		Stopwatch watchy = new Stopwatch();
		if(LOGGER.isTraceEnabled()) {
			// This can get spammy, so take countermeasures
			if(lastQuery.equals(s) == false) {
				// Prevent log spam
				LOGGER.trace(s);
				lastQuery = s;
				hasLoggedDup = false;
			} else if(hasLoggedDup == false) {
				LOGGER.trace("Suppressing repeated SQL message(s)...");
				hasLoggedDup = true;
			}
		}
		try {
			PreparedStatement prepareToPrepare = (PreparedStatement) batchStatement;
			if(prepareToPrepare == null) { 
				prepareToPrepare = con.prepareStatement(s);
			} 
			int x = 1;
			for(Object o : params) {
				// Tic tac toe	
				prepareToPrepare.setObject(x++, o);
			}
			if(batchMode == false) {
				boolean hasResults = prepareToPrepare.execute();
				if(watchy.getElapsed(TimeUnit.SECONDS) > 10) {
					LOGGER.debug("Query " + s + " took a long time to execute, " + watchy.getElapsed(TimeUnit.SECONDS) + " seconds");
				}
				if(hasResults) {
					return prepareToPrepare.getResultSet();
				}
			} else {
				prepareToPrepare.addBatch();
				batchStatement = prepareToPrepare;
			}
			return null;
		} catch (SQLException e) {
			if(e.getMessage().startsWith("Borrow")) {
				// This is dumbtarded
				throw new CloudException(e.getCause());
			}
			throw new CloudException(e);
		}
	}
	
	public T querySingle(ResultSet...rs) {
		try {
			if(Arrays.asList(rs).stream().allMatch(r -> wrappedNext(r)) == false) {
				return null;
			}
			return toObject(rs);
		} catch(Exception e) {
			throw new CloudException(e);
		}
	}
	
	public List<T> query(ResultSet...rs) {
		ArrayList<T> listy = new ArrayList<T>();
		ArrayList<ResultSet> subset = new ArrayList<ResultSet>(Arrays.asList(rs));
		// I know this is voodoo cruft
		subset.remove(0);
		subset.stream().allMatch(r -> wrappedNext(r));
		while(wrappedNext(rs[0])) {
			listy.add(toObject(rs));
		}
		
		return listy;
	}
	
	public abstract void tableCreateIfNeeded();
	public abstract void create(T v);
	public abstract void update(T v);
	public abstract void delete(T v);
	public abstract T toObject(ResultSet...r);
	
	private boolean wrappedNext(ResultSet rs) {
		try {
			return rs.next() == true;
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public static void executeWithRetryReal(CheckedRunnable r, String connectionName) throws SQLException {
		org.sqlite.SQLiteException exe = null;
		boolean hasLogged = false;
		for(int x = 0; x < RETRY_COUNT; ++x) {
			try {
				r.run();
				return;
			} catch(org.sqlite.SQLiteException sqle) { 
				if(testMessageForBusy(sqle.getMessage()) == false) {
					throw sqle;
				}
				exe = sqle;
				if(hasLogged == false) {
					LOGGER.warn("Retrying SQL due to database contention on " + connectionName);
					hasLogged = true;
				}
				try {
					Thread.sleep(RETRY_SLEEP_TIME_MS);
				} catch (InterruptedException e) {
					;
				}
			}
		}
		throw exe;
	}
	
	private static boolean testMessageForBusy(String msg) {
		msg = msg.toLowerCase();
		if(msg.contains("busy")) {
			return true;
		}
		if(msg.contains("locked")) {
			return true;
		}
		return false;
	}
}
