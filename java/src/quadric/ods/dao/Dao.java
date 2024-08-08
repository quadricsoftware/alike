package quadric.ods.dao;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.LockingMode;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteConfig.TempStore;
import org.sqlite.SQLiteConfig.TransactionMode;
import org.sqlite.SQLiteJDBCLoader;

import quadric.blockvaulter.CloudException;
import quadric.util.Flocker;
import quadric.util.Stopwatch;

public class Dao {
	private static final Logger LOGGER = LoggerFactory.getLogger( Dao.class.getName() );
	private static final int CHECKOUT_TIME_WARN_SECS = 60;
	
	private String dbPath;
	private String attachPath = null;
	private List<Crud<?>> crudz;
	//private int loanCount = 0;
	private int maxReaders;
	private int readerCount = 0;
	private boolean writerLock = false;
	private boolean shouldRun = true;
	private SQLiteConfig writeConfig;
	private SQLiteConfig readConfig;
	private Flocker flock = null;
	
	
	protected Dao(String dbPath, List<Crud<?>> toInit) {
		this(dbPath, toInit, false, false);
	}
	
	
	
	protected Dao(String dbPath, List<Crud<?>> toInit, boolean useWall, boolean allowSimul) {
		this.dbPath = dbPath;
		this.crudz = toInit;
		boolean isNativeMode = false;
		try {
			isNativeMode = SQLiteJDBCLoader.isNativeMode();
		} catch(Exception e) { ; }
		if(isNativeMode == false) {
			LOGGER.info("Sqlite native mode disabled!");
		}
		LOGGER.debug("Creating new dao at path " + dbPath);
		
		writeConfig = new SQLiteConfig();
		// Enable shared cache mode to reduce contention and improve performance?
		if(allowSimul) {
			//writeConfig.setSharedCache(true);
		}
		if(useWall) {
			writeConfig.setJournalMode(JournalMode.WAL);
		} else {
			writeConfig.setJournalMode(JournalMode.MEMORY);
		}
		writeConfig.setBusyTimeout(10000);
		writeConfig.setPageSize(4096);
		writeConfig.setSynchronous(SynchronousMode.FULL);
		writeConfig.setCacheSize(-10000);
		
		maxReaders = 0;
		if(allowSimul) {
			maxReaders = 3;
		}		
				
		// Initialize cruds
		try (Connection con = writeConfig.createConnection("jdbc:sqlite:" + dbPath)) {
			for(Crud<?> c : toInit) {
				c.setConnection(con);
				c.tableCreateIfNeeded();
			}
			//con.commit();
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
		
		// Set up read-only config
		readConfig = new SQLiteConfig(writeConfig.toProperties());
		readConfig.setReadOnly(true);
	}
	
	public void attach(String path) {
		this.attachPath = path;
	}
	
	public synchronized Connection getWriteConnection() {
		return getWriteConnection(false);
	}
	
	public synchronized Connection getWriteConnection(boolean autoCommit) {
		waitFor( () -> (writerLock == false), "writer lock");
		writerLock = true;
		waitFor( () -> (readerCount == 0), "exclusivisity");
		try {
			flock = new Flocker(getFlockFile());
			Connection con = writeConfig.createConnection("jdbc:sqlite:" + dbPath);
			con.setAutoCommit(autoCommit);
			con.createStatement().execute("PRAGMA temp_store = memory");
			//con.createStatement().execute("PRAGMA wal_autocheckpoint=0");
		
			Connection proxyConnection = makeProxy(con, false);
			if(attachPath != null) {
				LOGGER.trace("Attaching database " + attachPath);
				con.setAutoCommit(true);
				Crud<?> crud = crudz.get(0);
				crud.setConnection(proxyConnection);
				crud.buildQuery("ATTACH '" + attachPath + "' AS ATT");
				con.setAutoCommit(autoCommit);
			}
			
			logLend();
			
			return proxyConnection;
		} catch (SQLException |IOException e) {
			if(flock != null) {
				try {
					flock.close();
				} catch (IOException e1) {
					LOGGER.error("Problem closing flock for " + dbPath, e);
				}
			}
			writerLock = false;
			throw new CloudException(e);
		}
	}
	
	private String getFlockFile() {
		String base = "/tmp/wal-locker";
		return base + new File(dbPath).getName();
	}
	
	public synchronized Connection getReadOnlyConnection() {
		waitFor( () -> (readerCount < maxReaders && writerLock == false), "read-only lock");
		Connection proxyCon = null;
		Connection con = null;
		try {
			readerCount++;
			con = readConfig.createConnection("jdbc:sqlite:" + dbPath);
			proxyCon = makeProxy(con, true);
			logLend();
			return proxyCon;
		} catch(SQLException e) {
			readerCount--;
			throw new CloudException(e);
		}
		
	}
	
	
	public void closing(Connection con, boolean readOnly) {
		try {
			try {
				if(con.getAutoCommit() == false) {
					con.rollback();
				} else if(readOnly == false) {
					//throw new CloudException("Attempt to return database connection with autocommit of true");
				}
			} catch(SQLException sqle) {
				throw new CloudException(sqle);
			}
			if(attachPath != null) {
				LOGGER.debug("Detatching database " + attachPath);
				try {	
					con.setAutoCommit(true);
					try {
						crudz.get(0).buildQuery("DETACH ATT");
					} catch(Exception e) {
						if(e.getMessage().contains("ATT is locked")) {
							LOGGER.debug("SQLITE claims " + attachPath + " is locked, ignoring");
						} else {
							throw e;
						}
					} finally {
						con.setAutoCommit(false);
					}
				} catch(SQLException sqle) {
					throw new CloudException(sqle);
				}
				
			}
		} finally {
			LOGGER.trace("Returned connection to " + dbPath);
			synchronized(this) {
				try {
					con.close();
				} catch(SQLException sqle) {
					throw new CloudException(sqle);
				} finally {
					if(readOnly) {
						readerCount--;
					} else {
						writerLock = false;
						if(flock != null) {
							try {
								flock.close();
							} catch (IOException e) {
								LOGGER.error("Problem closing flock for " + dbPath, e);
							}
						}
					}
				}
				this.notifyAll();
			}
		}
	}
	
	/**
	 * Prevent others from getting a write connection
	 * @return
	 */
	public synchronized Closeable lockIt() {
		try {
			waitFor( () -> (writerLock == false), "writer lock");
			writerLock = true;
			waitFor( () -> (readerCount == 0), "exclusivisity");
			final Flocker flock = new Flocker(getFlockFile());
			return new Closeable() {
				@Override
				public void close() throws IOException {
					try {
						flock.close();
					} catch(IOException sqle) {
						throw new CloudException(sqle);
					} finally {
						writerLock = false;
					}
				}
			};
		} catch(Throwable e) {
			writerLock = false;
			if(e instanceof CloudException) {
				throw (CloudException) e;
			}
			throw new CloudException(e);
		}
	}
	

	public synchronized void close() {
		shouldRun = false;
		LOGGER.info("Closing Dao to " + dbPath);
	}
	
	private void waitFor(BooleanSupplier b, String reason) {
		Stopwatch t = new Stopwatch();
		boolean hasLogged = false;
		while(b.getAsBoolean() == false) {
			if(shouldRun == false) {
				throw new CloudException("Pool " + dbPath + " is closed");
			}
			try {
				this.wait(1000);
			} catch (InterruptedException e) {
				;
			}
			if(t.getElapsed(TimeUnit.SECONDS) > CHECKOUT_TIME_WARN_SECS) {
				if(hasLogged == false) {
					LOGGER.warn("Excessive wait on database " + dbPath + "; will keep waiting for " + reason);
					LOGGER.info(getLogLendString());
					hasLogged = true;
				}
			}
		}
		if(hasLogged) {
			LOGGER.info("Finished waiting on " + dbPath + " successfully");
		}
	}
	
	public String getDbPath() {
		return dbPath;
	
	}
	
	private Connection makeProxy(Connection con, boolean readOnly) {
		InvocationHandler voke = new InvocationHandler() { 
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable { 
				if("close".equals(method.getName())) { 
					closing(con, readOnly);
					return null;
				}
				try {
					return method.invoke(con, args); 
				} catch(InvocationTargetException e) {
					throw e.getCause();
				}
			}
		};
		return (Connection) Proxy.newProxyInstance(con.getClass().getClassLoader(), new Class[] {Connection.class}, voke); 
				
	}
	
	private void logLend() {
		if(LOGGER.isTraceEnabled()) {
			LOGGER.trace(getLogLendString());
		}
	}
	
	private String getLogLendString() {
		int writerCount = ((writerLock == true) ? 1 : 0);
		return "Connection to " + dbPath + " for " 
				+ getCaller() + " has " +
				+ (readerCount) + " active readers, " + writerCount + " active writers";
	}
	
	
	
	private String getCaller() {
		final List<String> callerz = new ArrayList<String>();
		
		new SecurityManager() {{
			 String caller = getClassContext()[2].getSimpleName();
			 callerz.add(caller);
		 }};
		 return callerz.get(0);
	}




	
	
}
