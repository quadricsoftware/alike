package quadric.ods;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.KurganCommander.Dispatch;
import quadric.blockvaulter.VaultSettings;
import quadric.legacy.NimbusVersion;
import quadric.util.Print;

/**
 * A file representing vault progress and the source command that started it all
 *
 */
public class ResumeFile {
	private static final Logger LOGGER = LoggerFactory.getLogger( ResumeFile.class.getName() );
	public static final int HEADER_SIZE = 1024 * 1024;
	public static final int BUFFERED_ITEM_COUNT = 512;
	
	ConcurrentLinkedQueue<Print> conQueue = new ConcurrentLinkedQueue<Print>();
	AtomicInteger vaultedCount = new AtomicInteger();
	NimbusVersion nb;
	long txNo;
	String json;
	
	/**
	 * Constructs a new ResumeFile used for outputting blocks in progress
	 */
	public ResumeFile(Dispatch d, NimbusVersion nb, long txNo) {
		this.nb = nb;
		this.txNo = txNo;
		this.json = d.json;

		if(d.json.length() > HEADER_SIZE) {
			throw new CloudException("Json command is larger than header area max size of" + HEADER_SIZE);
		}
		// Prepend some stuff meng
		try (FileOutputStream bus = new FileOutputStream(getFileName(), false)) {
			ByteBuffer byteMe = ByteBuffer.allocate(Integer.SIZE);
			byteMe.order(ByteOrder.LITTLE_ENDIAN);
			byteMe.putInt(json.length());
			bus.write(byteMe.array());
			bus.write(d.json.getBytes("US-ASCII"));
			
			FileChannel fc = bus.getChannel();
			fc.truncate(HEADER_SIZE);
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
	}
	
	
	public long getJobId() {
		return nb.getJobId();
	}
	
	public long getVmId() {
		return nb.getVmId();
	}
	
	/**
	 * Constructs a ResumeFile from an existing file
	 * @param path to the file
	 */
	public ResumeFile(String path) {
		//path = VaultSettings.instance().cleanWinePath(path);
		try (FileInputStream fis = new FileInputStream(path)) {
			ByteBuffer buffy = ByteBuffer.allocate(Integer.SIZE);
			buffy.order(ByteOrder.LITTLE_ENDIAN);
			fis.read(buffy.array());
			int len = buffy.getInt();
			byte [] awesome = new byte[len];
			fis.read(awesome);
			this.json = new String(awesome, Charset.forName("US-ASCII"));
			
			nb = new NimbusVersion();
			String filePart = new File(path).getName();
			try {
				String [] splitz = filePart.split("_");
				long jobId = Long.parseLong(splitz[0]);
				long vmId = Long.parseLong(splitz[1]); 
				txNo = Long.parseLong(splitz[2].split("\\.")[0]);
				nb.setJobId(jobId);
				nb.setVmId(vmId);
			} catch(Throwable t) {
				throw new CloudException("Filename cannot be parsed" + t);
			}
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		// Slam on a coda for compatability
		try (FileOutputStream fos = new FileOutputStream(path, true)) {
			String coda = "CODA";
			fos.write(coda.getBytes("US-ASCII"));
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		
	}
	
	public String getJsonHeader() {
		return json;
	}
	
	public int getVaultedCountEst() {
		return vaultedCount.get();
	}
	
	public void enqueue(Print p) {
		conQueue.add(p);
		vaultedCount.incrementAndGet();
	}
	
	public int dumpVaultedPrints() {
		int amtWritten = 0;
		FileOutputStream bus = null;
		int failures = 0;
		IOException death = null;
		List<Print> candidates = new ArrayList<Print>();
				
		try {
			while(failures < 20) {
				try {
					if(bus == null) {
						bus = new FileOutputStream(getFileName(), true);
					}
					candidates.clear();
					byte [] awesome = buffer(candidates);
					if(candidates.size() == 0) {
						break;
					}
					bus.write(awesome);
					bus.flush();
					amtWritten++;
					failures = 0;
				} catch(IOException ioe) {
					LOGGER.warn("Retrying cursor write due to networking connectivity issue to ADS: " + ioe.getMessage());
					conQueue.addAll(candidates);
					failures++;
					death = ioe;
					try {
						Thread.sleep(5000);
						if(bus != null) {
							bus.close();
						}
					} catch (Exception e) {;}
				}
			} // end "while"
		} finally {
			if(bus != null) {
				try {
					bus.close(); 
				} catch(IOException ioe) {
					throw new CloudException(ioe);
				}
			}
		}
		if(failures > 0) {
			throw new CloudException(death);
		}
		return amtWritten;
	}
	
	
	private byte []  buffer(List<Print> candidates) {
				
		for(int count =0; count < BUFFERED_ITEM_COUNT; ++count) {
			Print p = conQueue.poll();
			if(p == null) {
				break;
			}
			candidates.add(p);
		}
		ByteBuffer buffy = ByteBuffer.allocate(candidates.size() * (Print.PRINT_SIZE +1));
		for(Print p : candidates) {
			buffy.put(p.store());
			buffy.put((byte) '\n');
		}
		return buffy.array();
	}

	
	/**
	 * Deletes the underyling file representing this asset
	 */
	public void delete() {
		boolean rez = new File(getFileName()).delete();
		if(rez == false) {
			throw new CloudException("Delete of cursor resume file " + getFileName() + " failed");
		}
	}
	
	public String getFileName() {
		return VaultSettings.instance().getValidatePath() + File.separator + nb.getJobId() + "_" +  nb.getVmId() + "_" + txNo + ".cursor"; 
	}
	


}
