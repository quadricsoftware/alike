package quadric.fuse;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.crypt.CryptUtil;
import quadric.restore.CommandType;
import quadric.restore.RestoreHeader;
import quadric.restore.RestoreMonitor;
import quadric.socket.MungeServer;
import quadric.util.Pair;
import quadric.util.SharedMem;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;

/**
 * Routes all Fuse requests via pipes to appropriate subclasses
 *
 */
public class FuseMonitor {
	private static final Logger LOGGER = LoggerFactory.getLogger( FuseMonitor.class.getName() );
	
	private static String INTERPROC_DIR;
	private static String CONTROL_PIPE_PATH;
	public static final String OVERRIDE_SETTING = "fuseOverrideThreadCount";
	private static final int PATH_MAX_LEN = 10000;
	private static final String MAKE_PIPE_CMD = "mkfifo $1";
	private static final int MIN_WORKER_THREADS = 10;
	private static final int MAX_WORKER_THREADS = 100;
	private static final int THREAD_AUDIT_INTERVAL_SECS = 1; 
	
	
	public ExecutorService threadPool = null;
	//private ExecutorService threadPool = Executors.newCachedThreadPool();
	private RestoreMonitor restoreMon;
	private AmbMonitor ambMon;
	private MungeServer mungeMon;
	private volatile boolean shouldRun = true;
	private volatile boolean isPreshutdown = false;
	private boolean hasStartedUp = false;
	private Set<Pair<Long,Long>> activePipes = new HashSet<Pair<Long,Long>>();
	private static FuseMonitor me = new FuseMonitor();
	
	public SharedMem shared;
	
	private FuseMonitor() {; }
	
	public static FuseMonitor instance() {
		return me;
	}
	
	public void init() {
		shared = new SharedMem(0);
		
		INTERPROC_DIR = VaultSettings.instance().getInterprocBase();
		CONTROL_PIPE_PATH = INTERPROC_DIR + "/ku_control_pipe";
		int numThreads = (int) (VaultSettings.instance().getTotalMemory() / 1024L / 1024L / 28L);
		if(numThreads < MIN_WORKER_THREADS) {
			numThreads = MIN_WORKER_THREADS;
		} 
		if(numThreads > MAX_WORKER_THREADS) {
			numThreads = MAX_WORKER_THREADS;
		}
		String overrideStr = VaultSettings.instance().getSettings().get(OVERRIDE_SETTING);
		
		if(overrideStr != null && overrideStr.length() > 0) {
			try {
				int override = Integer.parseInt(overrideStr);
				if(override > 0) {
					LOGGER.info("FUSE thread count manually overrided via setting " + OVERRIDE_SETTING + "!");
					numThreads = override;
				}
			} catch(Throwable t) { ; }
			
		}
		new File(INTERPROC_DIR).mkdir();
		cleanOldPipes();
		LOGGER.info("Fuse monitor initializing with " + numThreads + " threads per fuse subsystem.");
		// Poop out a pipes.count file so clients can get in sync
		try (OutputStream os = new FileOutputStream(INTERPROC_DIR + "/pipes.count")) {
			// I am honestly not sure if this is even used by anyone
			int pipeCount = numThreads;
			if(pipeCount > 10) pipeCount = 10;
			String s = "" + pipeCount;
			os.write(s.getBytes("US-ASCII"));
			
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		int clientCount = 2;
		threadPool = Executors.newFixedThreadPool(numThreads * clientCount);
		restoreMon = new RestoreMonitor(this, numThreads);
		ambMon = new AmbMonitor(this);
		mungeMon = new MungeServer(numThreads);
		
		LOGGER.info("Monitoring control pipe " + CONTROL_PIPE_PATH);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdown();
		}));
		monitor();
		Stopwatch timey = new Stopwatch();
		synchronized(this) {
			while(hasStartedUp == false) {
				try {
					this.wait(1000);
				} catch (InterruptedException e) { ; }
			}
			if(timey.getElapsed(TimeUnit.SECONDS) > 30) {
				throw new CloudException("Timeout waiting on pipe startup");
			}
		}
		LOGGER.trace("hasStarted tripped, returning now");
	}
	
	public synchronized void preshutdown() {
		LOGGER.info("Pre-shutdown triggered");
		isPreshutdown = true;
		mungeMon.shutdown();
	}
	
	public synchronized void shutdown() {
		LOGGER.info("Shutdown triggered");
		shouldRun = false;
		try {
			shared.close();
		} catch (IOException e) {
				;
		}
	}
	
	private void monitor() {
		LOGGER.trace("Submitting doMonitor thread");
		threadPool.submit(() -> {
			doMonitor();
		});
	}
	
	public boolean isPreshutdown() {
		return isPreshutdown;
	}
	
	public InputStream getOutPipe(RestoreHeader header) throws IOException {
		String pipePath = INTERPROC_DIR + "/ku_" + header.clientId + "_" + header.pipeId + "_o";
		return new FileInputStream(pipePath);
	}
	
	/**
	 * Instead of using a pipe, uses a block file instead. Data is written to a .tmp version
	 * and then it's atomically renamed
	 */
	/*public OutputStream makeFileFor(RestoreHeader header) throws IOException {
		String outputPath = INTERPROC_DIR + "/ku_" + header.clientId + "_" + header.pipeId;
		final FileOutputStream fos = new FileOutputStream(outputPath, false);
		//fos.getChannel().truncate(0);
		OutputStream returnMe = new OutputStream() {
			@Override
			public void write(int arg0) throws IOException {
				fos.write(arg0);
			}
			
			@Override 
			public void write(byte [] b) throws IOException {
				fos.write(b);
			}
			
			@Override 
			public void write(byte [] b, int off, int len) throws IOException {
				fos.write(b, off, len);
			}
			
			
			@Override
			public void close() throws IOException {
				try {
					fos.close();
					super.close();
					//LOGGER.debug("Memset header is : " + header);
					Stopwatch watchy = new Stopwatch();
					//shared.set((int) header.pipeId, (int) header.clientId, header.reserved2);
					LOGGER.debug("Memset in " + watchy.getElapsed(TimeUnit.MICROSECONDS) + "us");
				} catch(Throwable t) { ; }
			}
		};
		return returnMe;
		
	}*/
	
	public OutputStream makePipeFor(RestoreHeader header) throws IOException {
		String outputPath = INTERPROC_DIR + "/ku_" + header.clientId + "_" + header.pipeId;
		synchronized(activePipes) {
			Pair<Long,Long> nicePair = new Pair<Long,Long>(header.clientId, header.pipeId);
			if(activePipes.contains(nicePair) == false) {
				//LOGGER.trace("Creating pipe at " + outputPath);
				makePipe(outputPath);
				activePipes.add(nicePair);
			}
		}
		return new FileOutputStream(outputPath);
		
	}
	
	private void doMonitor() {
		LOGGER.debug( "doMonitor starting up...");
		if(new File(CONTROL_PIPE_PATH).exists() == false) {
			try {
				LOGGER.info("Creating control pipe at path " + CONTROL_PIPE_PATH);
				makePipe(CONTROL_PIPE_PATH);
			} catch (IOException e) {
				LOGGER.error("Unable to open control pipe for reading", e);
				System.exit(0);
			}
		}
		
		byte [] buffy = new byte[new RestoreHeader().recordSize()];
		byte [] pathy = new byte[PATH_MAX_LEN];
		Stopwatch watchy = new Stopwatch();
		synchronized(this) {
			hasStartedUp = true;
			this.notify();
		}
		while(shouldRun) {
			LOGGER.trace("Opening control pipe");
			try (FileInputStream is = new FileInputStream(CONTROL_PIPE_PATH)) {
				while(shouldRun) {
					if(watchy.getElapsed(TimeUnit.SECONDS) > THREAD_AUDIT_INTERVAL_SECS) {
						//LOGGER.trace("Threadpool active size is currently " + ((ThreadPoolExecutor) threadPool).getActiveCount());
						watchy.getAndReset(TimeUnit.SECONDS);
					}
					RestoreHeader header = new RestoreHeader();
					LOGGER.trace("About to load next buf from stream...");
					if(VaultUtil.ezLoad(is, buffy, buffy.length) == false) {
						// No big deal, someone disconnected form the pipe
						continue;
					}
					header.load(buffy);
					if(header.pathLen > PATH_MAX_LEN) {
						throw new IOException("" + header.pathLen + " exceeds max path len of " + PATH_MAX_LEN);
					}
					if(header.pathLen == 0) {
						throw new IOException("Path element is too short!");
					}
			
					if(VaultUtil.ezLoad(is, pathy, header.pathLen) == false) {
						continue;
					}
					String thePath = new String(pathy, 0, header.pathLen, "US-ASCII");
					LOGGER.trace("Read a total of " + (header.pathLen + buffy.length) + " off pipe this time");
					final String thePath2 = cleanPath(thePath);
					threadPool.submit(() -> {
						dispatch(header, thePath2);
						
					});
				}
			} catch(IOException ioe) {
				LOGGER.error("Error reading restore request off pipe, will reconnect", ioe);
			}
		}
		LOGGER.info("Restore control thread is shutting down");
		try {
			new File(CONTROL_PIPE_PATH).delete();
			LOGGER.info("Control pipe deleted successfully");
		} catch(Throwable t) {
			LOGGER.error("Unable to delete control pipe");
		}
		
	}
	
	private void cleanOldPipes() {
		VaultUtil.listFiles(INTERPROC_DIR, "ku_*").forEach(s -> new File(s).delete());
	}
	
	private String cleanPath(String p) {
		if(p.equals("/")) return p;
		if(p.endsWith("/")) {
			return p.substring(0, p.length() -1);
		}
		return p;
	}
	
	
	private void dispatch(RestoreHeader header, String path) {
		LOGGER.trace("Dispatching header " + header + " at path " + path);
		try {
			Stopwatch watchy = new Stopwatch();
			CommandType command = null;
			try { command = CommandType.values()[header.command]; 
			} catch(Throwable t) { ;}
			if(command == null) {
				LOGGER.error("Unknown command passed to RestoreMonitor: " + header);
			}
			switch(command) {
				case data:
					restoreMon.handleRead(header, path);
					break;
				case attr:
					restoreMon.handleAttr(header, path);
					break;
				case list:
					restoreMon.handleList(header, path);
					break;
				case followLink:
					restoreMon.handleFollowLink(header, path);
					break;
				case openAmb: 
					ambMon.handleOpen(header, path);
					break;
				case writeAmb:
					ambMon.handleWrite(header, path);
					break;
					
			}
			if(watchy.getElapsed(TimeUnit.SECONDS) > 1) {
				LOGGER.debug("Command " + command + " took a long time to execute at " + watchy.getElapsed(TimeUnit.SECONDS) + " seconds");
				LOGGER.debug("Threadpool active size is currently " + ((ThreadPoolExecutor) threadPool).getActiveCount());
			}
		} catch(Throwable t) {
			LOGGER.error("Error handling dispatch for path: " + path + " with params: " + header.toString(), t);
		}
		
	}
	
	private void makePipe(String pipeName) throws IOException {
		if(new File(pipeName).exists() == false) {
			LOGGER.debug("Making pipe " + pipeName);
			VaultUtil.ezExec(MAKE_PIPE_CMD, pipeName);
		} else {
			LOGGER.trace("Will not make pipe: " + pipeName + " because is already exists");
		}
		
	}

}
