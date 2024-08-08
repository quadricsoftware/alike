package quadric.socket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.crypt.CryptUtil;
import quadric.fuse.AmbHelper;
import quadric.fuse.FuseMonitor;
import quadric.ods.KurganAmbHeader;
import quadric.spdb.KurganBlock;
import quadric.spdb.KurganBlock.BlockSettings;
import quadric.util.Print;
import quadric.util.Stopwatch;
import quadric.util.VaultUtil;

public class MungeServer {
	private static final Logger LOGGER = LoggerFactory.getLogger( MungeServer.class.getName() );
	private static final String MUNGE_CONF_FILE = "/home/alike/Alike/agentShare/munge.conf";
	private static final int BLOCK_SIZE_MAX = 1024 * 1024 * 10;
	private static final int BLOCK_SIZE_MIN = 9;
	private static final int AMB_PATH_LEN_MAX = 1024;
	private static final int RECEIVE_SOCKET_BUFFER_SIZE = 1024 * 128;
	private static final int SELECT_TIMEOUT_MS = 2000;
	private static final int TOKYO_DRIFT_MAX_SECS = 60 * 10;
	//private static final int SEND_BLOCK_WAIT_MAX_MS = 500;

	public static final int KERNEL_WARNING_TIMEOUT_MINS = 10;
	public static final int REZ_IN_PROGRESS = -1;
	public static final int REZ_OK = 0;
	public static final int REZ_IO = 5;
	public static final int REZ_BUSY = 6;

	Selector selector;
	private InetSocketAddress listenAddress;
	private Map<SocketChannel,Bucket> socketClients = new HashMap<SocketChannel,Bucket>();
	private Map<String,MuInt> clientCount = new HashMap<String,MuInt>();
	AtomicInteger activeCount = new AtomicInteger();
	private volatile boolean shouldRun = true;
	private BlockSettings bs = VaultSettings.instance().makeKurganSets();
	int threadCountMax;
	int maxSocksPerClient;
	int incomingBlockMax;
	private ServerSocketChannel serverChannel;
	private SelectionKey serverSocketKey;
	private String mySecret;

	public MungeServer(int threadCount) {
		boolean abort = false;
		if(VaultSettings.instance().isFuseMungeEnabled()) {
			LOGGER.warn("Legacy fuse munge enabled, socket munge will not be used!");
			abort = true;
		} else if(VaultSettings.instance().getAdapter(0).isReadOnly()) {
			LOGGER.info("READ-ONLY MODE enabled, will not listen for socket clients");
			abort = true;
		}
		if(abort) {
			shouldRun = false;
			// Delete this file if it previously existed
			new File(MUNGE_CONF_FILE).delete();
			return;
		}
		ByteBuffer buffy = ByteBuffer.allocate((int) (new File(MUNGE_CONF_FILE)).length());
		String foo = null;
		try (FileInputStream fis = new FileInputStream(MUNGE_CONF_FILE)) {
			FileChannel fc = fis.getChannel();
			VaultUtil.ezLoad(fc, buffy);
			foo = new String(buffy.array(), "US-ASCII");
		} catch(IOException io) {
			throw new CloudException("Munge conf file not found at " + MUNGE_CONF_FILE);
		}
		String [] splitz = foo.split(",");
		if(splitz.length < 3) {
			throw new CloudException("Munge.conf contents cannot be parsed: " + foo);
		}
		mySecret = splitz[2];
		
		int port = VaultSettings.instance().getMungePort();
		maxSocksPerClient = VaultSettings.instance().getMaxSocksPerClient();
		this.threadCountMax = threadCount;
		this.incomingBlockMax = VaultSettings.instance().makeKurganSets().blockSizeBytes;
		// Deal with larger block sizes, which consume more memory
		int weakener = incomingBlockMax / 524288;
		this.threadCountMax  /= weakener;
		if(this.threadCountMax < 2) {
			this.threadCountMax = 2;
		}
		// Max compressed can actually be larger than the block, which is rad
		this.incomingBlockMax = KurganBlock.calcMaxBlockSize(incomingBlockMax);
		// Save some poopy for metadata and other fuzz
		this.incomingBlockMax += 2048;
		LOGGER.debug("Socket server incoming block max is " + incomingBlockMax);
		
		//this.threadCountMax = 2;

		try {
			this.selector = Selector.open();
			serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);

			// retrieve server socket and bind to port
			listenAddress = new InetSocketAddress(port);
			serverChannel.socket().bind(listenAddress);
			serverSocketKey = serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
		} catch(SocketException se) {
			if(se.getMessage().toLowerCase().contains("permission denied")) {
				String msg = "Port " + port + " cannot be bound to. Please select an available port greater than 1024 for your socket traffic in the WebUI Network settings--system default is 2811";
				LOGGER.error(msg);
				throw new CloudException(msg); 
			} 
			throw new CloudException(se);
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}

		Thread t = new Thread( () -> {
			start();
		});
		t.start();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdown();
		}));

		int socketTimeoutSecs = VaultSettings.instance().getSocketTimeoutSecs();
		LOGGER.info("Munge server listening on port " + port
				+ " with a maximum of " + threadCountMax + " active block writers. Use " + FuseMonitor.OVERRIDE_SETTING + " setting to adjust this.");
		if(socketTimeoutSecs < 60 * 10) {
			LOGGER.info("Munge server currently set with aggressive socket timeout of " + socketTimeoutSecs + " seconds");
		} else {
			LOGGER.debug("Munge server socket timeout set to " + socketTimeoutSecs + " seconds.");
		}
	}

	public void shutdown() {
		if(shouldRun == true) {
			shouldRun = false;
			this.selector.wakeup();
		}
	}

	private void start()  {
		boolean canReadFromSocks = true;
		List<SelectionKey> badKeys = new ArrayList<SelectionKey>();
		long socketTimeoutSecs = VaultSettings.instance().getSocketTimeoutSecs();
		Stopwatch settingsRefreshTimer = new Stopwatch();
		Stopwatch notReadingOffSocketsTimer = new Stopwatch();
		boolean hasWarnedOfKernelIssue = false;
		while(shouldRun) {
			try {
				// Sometimes we are too busy to read off socks
				
				if(activeCount.get() >= threadCountMax) {
					if(canReadFromSocks == true) {
						LOGGER.trace("Sockets busy, will wait on reads");
						canReadFromSocks = false;
						notReadingOffSocketsTimer.reset();
					} else if(notReadingOffSocketsTimer.getElapsed(TimeUnit.MINUTES) > KERNEL_WARNING_TIMEOUT_MINS
							&& hasWarnedOfKernelIssue == false) {
						LOGGER.error("Socket block write subsystem may be deadlocked due to kernel issue!");
						hasWarnedOfKernelIssue = true;
					}
				} else if(canReadFromSocks == false) {
					LOGGER.trace("Sock reads ok again.");
					canReadFromSocks = true;
				}
				badKeys.clear();
				if(settingsRefreshTimer.getElapsed(TimeUnit.MINUTES) > 2) {
					socketTimeoutSecs = VaultSettings.instance().getSocketTimeoutSecs();
					settingsRefreshTimer.reset();
				}
				for(Map.Entry<SocketChannel,Bucket> e : socketClients.entrySet()) {
					Bucket b = e.getValue();
					if(System.currentTimeMillis() - b.last > (socketTimeoutSecs * 1000)) {
						LOGGER.info("Closing stale socket at " + b.remoteHost);
						badKeys.add(b.key);
						continue;
					}
					if(b.result != REZ_IN_PROGRESS) {
						//LOGGER.debug("Registering key for writing");
						b.key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
					} else if(canReadFromSocks /*|| b.payloado.position() == 0 */) {
						b.key.interestOps(SelectionKey.OP_READ);
					} else {
						// Let new sockets start writing, it seems to bother win clients otherwise
						b.key.interestOps(0);
					}
					
				}
				for(SelectionKey k : badKeys) {
					closeIt(k);
				}
				// wait for events
				this.selector.select(SELECT_TIMEOUT_MS);
				

				//work on selected keys
				Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();

				while (keys.hasNext()) {
					SelectionKey key = (SelectionKey) keys.next();

					if(key.isValid() && key.isAcceptable()) {
						accept(key);
					}
					if(key.isValid() && key.isReadable()) {
						read(key);
					} 
					if(key.isValid() && key.isWritable()) {
						write(key);
					}

					// this is necessary to prevent the same key from coming up 
					// again the next time around.
					keys.remove();
				} // End inner while
			} catch(IOException ioe) {
				// Suppress silly peer reset errors
				if(ioe.getMessage().contains("reset by peer") == false) {
					LOGGER.error("Error in munge socket communications", ioe);
				}
			} catch(Throwable t) {
				LOGGER.error("Error in mungeServer start", t);
			}
		} // end outer while

		LOGGER.info("Initiating clean MungeServer socket shutdown");
		// Shutdown gracefully, closing the well-known server socket first
		try {
			serverChannel.close();
		} catch (IOException e) {
			LOGGER.error("Error shutting down server socket", e);
		}
		// Now kill clients
		for(SocketChannel c : socketClients.keySet()) {
			try {
				c.close();
			} catch(Throwable t) { ;}
		}
		LOGGER.info("MungeServer socket shutdown complete");
	}

	//accept a connection made to this channel's socket
	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
		SocketChannel channel = serverChannel.accept();
		if(channel == null) {
			// This is kinda odd, but totally possible in nonblocking mode
			return;
		}
		channel.configureBlocking(false);
		Socket socket = channel.socket();      
		String sockAddr = channel.socket().getRemoteSocketAddress().toString().split(":")[0];
		MuInt m = clientCount.get(sockAddr);
		if(m == null) {
			m = new MuInt();
		} else {
			m.m++;
		}
		if(m.m > maxSocksPerClient) {
			LOGGER.debug("Client at " + sockAddr + " has attempted too many sockets, will restrict");
			try {
				socket.close();
			} catch(IOException ioe) {
				LOGGER.debug("Error closing socket that is over accept limit for client", ioe);
			}
			return;
		}
		clientCount.put(sockAddr, m);
		socket.setReceiveBufferSize(RECEIVE_SOCKET_BUFFER_SIZE);

		// register channel with selector for further IO
		LOGGER.debug("Registered new socket " + socketClients.size() + " from " + sockAddr 
				+ ", current active writer count is " + activeCount.get() + ", this client has " + m.m + " sockets open");
		SelectionKey key2 = channel.register(this.selector, SelectionKey.OP_READ);
		Bucket b = new Bucket(this, channel.socket().getRemoteSocketAddress().toString(), key2);
		socketClients.put(channel, b);
	}




	private void write(SelectionKey key) throws IOException {
		//LOGGER.debug("Entering socket channel write...");
		SocketChannel channel = (SocketChannel) key.channel();
		//LOGGER.debug("Entering socket write for " + channel.socket().getRemoteSocketAddress());
		Bucket b = socketClients.get(channel);
		if(b == null) {
			// This guy is already dead, he just doesn't know it yet
			return;
		}
		if(b.result == REZ_IN_PROGRESS) {
			SocketAddress sa = channel.socket().getRemoteSocketAddress();
			String remoteGuy = "???";
			if(sa != null) {
				remoteGuy = sa.toString();
			}
			LOGGER.error("Socket at " + remoteGuy + " in illegal state, disconnecting, current active writer count is " + activeCount.get());
			closeIt(key);
		}

		b.last = System.currentTimeMillis();
		ByteBuffer bb = ByteBuffer.allocate(1);
		bb.put((byte) b.result);
		bb.flip();
		try {
			if(channel.write(bb) > 0) {
				// We can reuse this connection for the next block they wanna send us
				b.reset();

			} else {
				//LOGGER.debug("Channel busy for writes, will register for write interest...");
				return;
			}
		} catch(IOException ioe) {
			LOGGER.error("Error writing to client, connection will be closed. Error is ", ioe);
			closeIt(key);
			
		}



		//LOGGER.debug("Wrote to socket!");
	}

	//read from the socket channel
	private void read(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		Bucket b = socketClients.get(channel);
		int numRead = -1;
		ByteBuffer buffer = null;
		if(b == null) {
			LOGGER.debug("Attempt to send to socket that was never created or was timed out");
			numRead = -1;
		} else {
			buffer = b.payload();
			numRead = channel.read(buffer);
			//LOGGER.trace("Read " + numRead + " off " + channel.socket().getRemoteSocketAddress());
		}
		if (numRead == -1) {
			closeIt(key);
			return;
		}
		// Keep this bucket active
		b.last = System.currentTimeMillis();

		if(buffer.position() > Bucket.SOCKET_SPECIFIC_PREAMBLE_SIZE) { 
			if(checkCredential(b) == false) {
				LOGGER.error("Illegal credentials presented for " + channel.socket().getRemoteSocketAddress());
				closeIt(key);
				return;
			}
		} else {
			// We have not yet read enough off the wire to really do anything...besides
			// fall on our face and explode
			return;
		}

		int targLen = b.getTargetLen(); 
		//LOGGER.debug("**Target len is " + targLen);

		if(targLen < BLOCK_SIZE_MIN || targLen > BLOCK_SIZE_MAX) {
			LOGGER.error("Illegal block size detected for " + channel.socket().getRemoteSocketAddress());
			closeIt(key);
			return;
		}
		// Did they fill up? Booya!
		if(b.isReady() == true) {
			try {
				//LOGGER.debug("Buffer is ready, here we go");
				b.payload().flip();
				sendBlock(b, key);
			} catch(CloudException ce) {
				LOGGER.error("Error processing block write", ce);
				// Set this to error code 5
				b.result = MungeServer.REZ_IO;
			} catch(Throwable t) {
				LOGGER.error("Error processing block write, connection will be closed", t);
				closeIt(key);
			}
		}
		return;
	}

	private void sendBlock(Bucket b, SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		ByteBuffer buffer = b.payload();
		buffer.position(Bucket.SOCKET_SPECIFIC_PREAMBLE_SIZE);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		int ambPathLen = buffer.getInt();
		if(ambPathLen < 0 || ambPathLen > AMB_PATH_LEN_MAX) {
			LOGGER.error("Illegal AMB path length of detected for " + channel.socket().getRemoteSocketAddress());
			closeIt(key);
			return;
		}
		byte [] pathBites = new byte[ambPathLen];
		buffer.get(pathBites);
		String path = "";
		try {
			path = new String(pathBites, "US-ASCII");
		} catch(Throwable t) { ;}

		//LOGGER.debug("Going to send block at path " + path);
		KurganAmbHeader ambHeader = new KurganAmbHeader();
		byte [] ambHeaderBites = new byte[ambHeader.recordSize()];
		buffer.get(ambHeaderBites);
		ambHeader.load(ambHeaderBites);
		if(ambHeader.getBlockSize() < BLOCK_SIZE_MIN || ambHeader.getBlockSize() > buffer.remaining()) {
			LOGGER.error("Illegal block header detected for " + channel.socket().getRemoteSocketAddress());
			closeIt(key);
			return;
		}
		
		//LOGGER.debug("AMB header here: " + ambHeader);
		activeCount.incrementAndGet();
		try {
			byte [] kurgan = new byte[ambHeader.getBlockSize()];
			buffer.get(kurgan);
			KurganBlock kb = new KurganBlock(kurgan, ambHeader.getBlockSize(), ambHeader.getPrint().toString());
			final String path2 = path;
			FuseMonitor.instance().threadPool.submit(() -> {
				doSendBlock(key, kb, b, path2);
			});
		} catch(Throwable t) {
			activeCount.decrementAndGet();
			throw t;
		}
		return;
	}

	private void doSendBlock(SelectionKey key, KurganBlock kb, Bucket b, String path) {
		try {
			//SocketChannel channel = (SocketChannel) key.channel();
			//LOGGER.debug("Block send in progress...");
			int txNo = AmbHelper.getOrRegister(path);
			AmbHelper.doWriteBlock(bs, kb, path, txNo);
			b.result = REZ_OK;
		} catch(Throwable ce) {
			LOGGER.error("Error processing block write", ce);
			b.result = REZ_IO;
		} finally {
			// Allow another party to write
			activeCount.decrementAndGet();
			// Awaken the beast so it sends our cruft
			this.selector.wakeup();
			//try {
			//write(key);
			/*} catch(IOException ioe) {
			  LOGGER.error("Unable to register client socket for writing", ioe);
			  closeIt(b.key);
		  }*/
		}
	}

	private void closeIt(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		Bucket b = socketClients.get(channel);
		socketClients.remove(channel);
		
		key.interestOps(0);
		key.cancel();
		if(b != null) {
			b.reset();
		}
		Socket socket = channel.socket();
		SocketAddress remoteAddr = socket.getRemoteSocketAddress();
		MuInt m = clientCount.get(remoteAddr.toString().split(":")[0]);
		if(m != null) {
			m.m--;
			if(m.m == 0) {
				clientCount.remove(remoteAddr);
			}
		}
		LOGGER.debug("Socket connection will be closed: " + remoteAddr + "; current active socket count is " + socketClients.size() 
						+ ", current active writer count is " + activeCount.get());
		try {
			channel.close();
		} catch(IOException ioe) { ;} 
	}

	private boolean checkCredential(Bucket b) {
		/*if(System.currentTimeMillis() - (b.getCredentialTime() * 1000) > TOKYO_DRIFT_MAX_SECS) {
		  LOGGER.error("Timestamp from peer " + b.remoteHost + " is way out of date!");
		  return false;
	  }*/
		String myCredential = b.getCredentialTime() + mySecret;
		String md5 = "";
		try {
			md5 = CryptUtil.makeMd5Hash(myCredential.getBytes("US-ASCII"));
		} catch (UnsupportedEncodingException e) {
			;
		}
		if(b.getCredentialMd5().equals(md5) == false) {
			LOGGER.debug("Client credential time is: " + b.getCredentialTime() + ", credential hash is: '" + b.getCredentialMd5() + "' vs ours of '" + md5 + "'");
			//LOGGER.debug("Sanity is " + b.getCredentialSanity());
			return false; 
		}
		return true;
	}
}

/**
 * Block has 
 * credentials -- 24 bytes
 * targetLen -- 4 bytes
 * payload -- variable
 * 
 * payload has
 * ambPathLen
 * ambPath
 * ambHeader
 * block data
 * 
 *
 */
class Bucket {
	private static final Logger LOGGER = LoggerFactory.getLogger( Bucket.class.getName() );
	public static final int SOCKET_SPECIFIC_PREAMBLE_SIZE = 28;
	SelectionKey key;

	private ByteBuffer payloado = null;
	volatile int result;
	long last;
	MungeServer daddy;
	String remoteHost;

	Bucket(MungeServer daddy, String remoteHost, SelectionKey key) {
		this.key = key;
		this.remoteHost = remoteHost;
		this.daddy = daddy;
		resetReal(false);
	}

	ByteBuffer payload() {
		if(payloado == null) {
			payloado = ByteBuffer.allocate(daddy.incomingBlockMax);
			payloado.order(ByteOrder.LITTLE_ENDIAN);
		}
		return payloado;
	}
	void reset() {
		resetReal(true);
	}

	int getTargetLen() {
		payload();
		return payloado.getInt(24);
	}

	boolean isReady() {
		payload();
		//LOGGER.debug("isReady check, position is " + payloado.position() + " buffer remaining is " + payloado.remaining());
		if(payloado.position() > SOCKET_SPECIFIC_PREAMBLE_SIZE) {
			int targLen = getTargetLen();
			if(payloado.position() == targLen + SOCKET_SPECIFIC_PREAMBLE_SIZE) {
				return true;
			}
		}
		return false;
	}

	String getCredentialMd5() {
		payload();
		byte [] md5 = new byte[16];
		int oldPos = payloado.position();
		payloado.position(8);
		payloado.get(md5);
		payloado.position(oldPos);
		return new Print(md5).toString();
	}

	long getCredentialTime() {
		payload();
		return payloado.getLong(0);
	}

	void resetReal(boolean returnGuys) {
		if(payloado != null) {
			// Free up some memory, if needed
			if(daddy.activeCount.get() > daddy.threadCountMax) {
				payloado = null;
			} else {
				// Buffer reuse
				payloado.clear();
			}
		}
		result = MungeServer.REZ_IN_PROGRESS;
		last = System.currentTimeMillis();
	}
}


class MuInt {
	public int m = 1;
}