package quadric.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.VaultSettings;
import quadric.crypt.CryptUtil;
import quadric.ods.dao.Dao;

public class VaultUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger( VaultUtil.class.getName() );
	private static final int FILE_BUFFER_SIZE = (4096 *2);
	
	
	
	public static String prettyTruncate(String value, int length) {
		{
		  if (value != null && value.length() > length) {
		    value = value.substring(0, length);
		  	value += "..";
		  }
		  return value;
		}
		
	}
	/**
	 * 
	 * @param command the comand to run, such as ls -la fooman
	 * @param replacements if you used $1-style variables in your command, these are your replacement keys
	 * @return results from standard out (please redirect stderr if you need it!)
	 * @throws IOException
	 */
	public static String ezExec(String command, String...replacements) throws IOException {
		String [] f = command.split(" ");
		List<String> screwed = new ArrayList<String>();
		for(String s : f) {
			s = replaceArgs(s, replacements);
			screwed.add(s);
		}
		String [] dispicible = screwed.toArray(new String[0]);
		Stopwatch watchy = new Stopwatch();
		Process p = Runtime.getRuntime().exec(dispicible);
		String val = "";
		try (Scanner s = new Scanner(p.getErrorStream()).useDelimiter("\\A") ) {
	        if (s.hasNext()) {
	            val = s.next();
	        }
	        else {
	          val = "";
	        }
		}
		String val2 = ""; 
		try (Scanner s = new Scanner(p.getInputStream()).useDelimiter("\\A") ) {
	        if (s.hasNext()) {
	            val2 = s.next();
	        }
	        else {
	          val2 = "";
	        }
		}
		val = val + val2;
        while(true) {
        	try {
        		p.waitFor();
        		break;
        	} catch (InterruptedException e) {;}
        }
        if(LOGGER.isDebugEnabled()) {
        	// Careful, some commands have passwords in them!
        	//LOGGER.trace("Executed in: " + watchy.getElapsed(TimeUnit.MILLISECONDS) + "ms " + String.join(" ", dispicible));
        }
        return val;
	}
	
	/**
	 * Replace arguments $1 $2 $3...$n (starting with $1, not $0) found in cmd with args
	 * @param cmd the command which contains variables such as $1 $2
	 * @param args the replacement values, in order 
	 */
	public static String replaceArgs(String cmd, String...args) {
		int pos = 0;
		for(String s : args) {
			pos++;
			cmd = cmd.replace("$" + pos, s);
		}
		return cmd;
	}
	
	
	public static void toByteStruct(GetResult rez, ByteStruct out) {
		byte [] bites = new byte[(int) rez.len];
		toBytes(rez, bites);
		out.load(bites);
		
	}
	
	public static void toBytes(GetResult rez, byte [] bites) {
		try (InputStream tmpy = rez.in) {
			if(rez.in.read(bites) != rez.len) {
				throw new CloudException("Unable to read struct from get result");
			}
		} catch(IOException e) {
			throw new CloudException(e);
		}	
	}
	
	
	/*public static String fixUuid(final String orig) {
		return orig.replaceAll("[\\W]|_", "").toUpperCase();
	}*/
	
	
	
	public static void reduce(int threadCount, Runnable r) {
		ForkJoinPool forkyAndBess = new ForkJoinPool(threadCount);
		
		forkyAndBess.execute(r);
		forkyAndBess.shutdown();
		while(true) {
			try {
				if(forkyAndBess.awaitTermination(200, TimeUnit.MILLISECONDS) == true) {
					break;
				}
			} catch (InterruptedException e) {
				;
			}
		}
		
			
	}
	
	/**
	 * Used for when you want to wrap one progress callback inside another for a substep
	 * Also handles averaging across threads, if needed
	 * @param dp the parent progress 
	 * @param current the current progress %
	 * @param stepPercent the amount this progress can increase by, in percentage terms (e.g. 1, 5, 50, 99)
	 * @return a wrapper suitable for passing into another method that takes progress
	 */
	public static DoubleConsumer nestedProgress(DoubleConsumer dp, double current, double stepPercent) {
		boolean multithreaded = true;
		final Map<Long, Double> threadProgress = new TreeMap<Long,Double>();
		final Stopwatch watchy = new Stopwatch();
		return new DoubleConsumer() {
			@Override
			public void accept(double value) {
				if(value < 100 && watchy.getElapsed(TimeUnit.MILLISECONDS) < 50) {
					// Avoid spamming the mutex below
					return;
				}
				watchy.getAndReset(TimeUnit.MILLISECONDS);
				synchronized(this) {
					double proggy = 0;
					if(multithreaded) {
						long myThreadId = Thread.currentThread().getId();
						threadProgress.put(myThreadId, value);
						try {
							// Average the values across threads
							proggy = threadProgress.entrySet().stream().mapToDouble(e -> e.getValue()).average().getAsDouble();
						} catch(NoSuchElementException nsee) { ; }
						if(proggy == 0) {
							proggy = value;
						}
					} else {
						proggy = value;
					}
					double radly = (proggy / 100.00 * stepPercent) + current;
					if(multithreaded == false) {
						dp.accept(radly);
					} else {
						// Only the first thread can update, the others just help average out
						if(Thread.currentThread().getId() == threadProgress.keySet().iterator().next()) {
							dp.accept(radly);
						}
					}
				}
				
			}
		};
	}
	
	public static PathedGetResult getBlockViaFile(CloudAdapter adp, String path)  {
		return getBlockViaFile(adp, path, null);
	}
	
	/**
	 * Downloads a (larger) block to a temp file to avoid connectivity issues for sustained processing times
	 * 
	 */
	public static PathedGetResult getBlockViaFile(CloudAdapter adp, String path, DoubleConsumer progress) {
		try {
			// Create the cache file locally
			GetResult rez = adp.getBlock(path, 0);
			final File f = File.createTempFile("getblock", "tmp", new File(VaultSettings.instance().getTempPath(rez.len)));
			f.deleteOnExit();
			try(InputStream is = rez.in; FileOutputStream fos = new FileOutputStream(f.getPath())) {
				byte [] puke = new byte[FILE_BUFFER_SIZE];
				for(long pos = 0; pos < rez.len;) {
					int amtToRead = FILE_BUFFER_SIZE;
					if(amtToRead > rez.len - pos) {
						amtToRead = (int) (rez.len - pos);
					}
					int i = is.read(puke, 0, amtToRead);
					if(i == -1) {
						throw new CloudException("EOF unexpected");
					}
					fos.write(puke, 0, i);
					pos += i;
					if(progress != null) {
						double awesome = pos / rez.len * 100.00;
						progress.accept(awesome);
					}
					
				}
			} catch(IOException e) {
				throw new CloudException(e);
			}
			rez.in = new FileInputStream(f.getPath()) {
				@Override
				public void close() throws IOException {
					super.close();
					// Nuke the temp file now that they are done with it
					f.delete();
					
				}
			};
			if(progress != null) {
				progress.accept(100);
			}
			PathedGetResult pathed = new PathedGetResult(rez);
			pathed.localPath = f.getPath();
			return pathed;
		} catch (IOException e1) {
			throw new CloudException(e1);
		}
		
	}
	
	public static String getBlockToString(CloudAdapter adp, String path) {
		GetResult rez = adp.getBlock(path, 0);
		StringBuilder sb = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(rez.in, "US-ASCII"))) {
			String wad;
			while((wad = br.readLine()) != null) {
				sb.append(wad);
				sb.append('\n');
			}
		} catch(IOException exce) {
			throw new CloudException(exce);
		}
		// Truncate stream as needed
		sb.setLength((int) rez.len);
		return sb.toString();
	}
	
	
	public static void putBlockFromFile(CloudAdapter adp, String localPath, long localPathMax, String destPath, DoubleConsumer progress, JobControl control) {
		File tmp = new File(localPath);
		long total = localPathMax;
		long current = 0;
		byte [] buffy = new byte[FILE_BUFFER_SIZE];
		try (FileInputStream fis = new FileInputStream(localPath);
				OutputStream os = VaultUtil.putBlockFromFile(adp, destPath, total).first) {
			while(true) {
				int i = fis.read(buffy);
				if(i == -1) break;
				os.write(buffy, 0, i);
				current+= i;
				
				double prog = ((double) current / total)  * 100.00;
				if(progress != null) {
					progress.accept(prog);
				}
				if(control != null) {
					control.control();
				}
			}
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
	}
	
	public static void putBlockFromFile(CloudAdapter adp, String localPath, String destPath, DoubleConsumer progress, JobControl control) {
		putBlockFromFile(adp, localPath, new File(localPath).length(), destPath, progress, control);
	}
	
	/**
	 * Puts a block via a temporary file
	 * @param adp the destination to write to
	 * @param name the destination object name
	 * @return
	 */
	public static Pair<OutputStream,MessageDigest> putBlockFromFile(CloudAdapter adp, String name, long projectedSize) {
		try {
			final File f = File.createTempFile(name, "vlt", new File(VaultSettings.instance().getTempPath(projectedSize)));
			f.deleteOnExit();
			final DigestOutputStream dos = CryptUtil.getRollingMd5(new BufferedOutputStream(new FileOutputStream(f)));
			
			OutputStream returnMe = new OutputStream() {
				@Override
				public void write(int arg0) throws IOException {
					dos.write(arg0);
				}
				
				@Override
				public void close() throws IOException {
					try {
						dos.close();
						super.close();
						try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(f))) {
							String fiver = CryptUtil.bytesToHex(dos.getMessageDigest().digest());
							adp.putBlock(name, fis, f.length(), fiver);
						} catch(IOException e) {
							throw new CloudException(e);
						}
					} finally {
						f.delete();
					}
				}
			};
			return new Pair<OutputStream,MessageDigest>(returnMe, dos.getMessageDigest());
		} catch(IOException e) {
			throw new CloudException(e);
		}
	}
	
	public static String putBlockFromMem(ByteStruct<?> struct, CloudAdapter adp, String target) {
		byte [] bites = struct.store();
		return putBlockFromMem(bites, bites.length, adp, target);
	}	
	
	/*public static String putKBlockFromMem(byte [] bites, CloudAdapter adp, String target) {
		ByteArrayInputStream streamy = new ByteArrayInputStream(bites);
		adp.putBlock(target, streamy, bites.length, target);
		return target;
	}*/

	
	public static String putBlockFromMem(byte [] bites, int len, CloudAdapter adp, String target) {
		//Stopwatch watchy = new Stopwatch();
		SeeThruByteArrayInputStream streamy = new SeeThruByteArrayInputStream(bites);
		String dr5 = "";
		boolean checkDr5 = true;
		if(target.length() == 32 && target.contains(".")== false) {
			if(adp.cantVerifyKurganBlocks() == true) {
				//LOGGER.trace("Skipping md5 for block");
				checkDr5 = false;
			}
		}
		if(checkDr5) {
			dr5 = CryptUtil.makeMd5Hash(bites);
		}
		boolean storedAnything = adp.putBlock(target, streamy, len, dr5);
		//LOGGER.trace("Completed in " + watchy.getElapsed(TimeUnit.MICROSECONDS) + " micros");
		if(storedAnything == false && checkDr5 == false) {
			return null;
		}
		return dr5;
	}
	
	public static int binarySearchInHcl(Print target, String hclPath) {
		try (FileChannel fc = FileChannel.open(Paths.get(hclPath), StandardOpenOption.READ)) {
			int recordCount = (int) (fc.size() - HclReaderUtil.CODA.length()) / HclReaderUtil.RECORD_SIZE;
			recordCount--;
			Print t2 =  binarySearch(target, fc, 0, recordCount, 0, true);
			if(t2 != null) {
				int recordSizeWithNewline = target.recordSize() +1;
				return (int) (fc.position() / recordSizeWithNewline);
			}
			return -1;
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
	}
	
	public static void verifyBinaryHclOrder(String hclPath) {
		HclReaderUtil crappy = new HclReaderUtil(hclPath);
		HclCursor c = crappy.createCursor();
		Print prev = new Print("00000000000000000000000000000000");
		while(c.hasNext()) {
			Print p = c.next();
			if(p.compareTo(prev) < 1) {
				throw new CloudException("Order is wrongo");
			}
			prev = p;
		}
		
	}
	
	/**
	 * 
	 * @param target the item being searched for
	 * @param ch the channel to search within
	 * @param maxEntry the max entry you want to search
	 * @param skipBytes bytes to skip at the beginning
	 * @return
	 */
	public static <T extends ByteStruct<T>> T binarySearch(T target, FileChannel ch, int maxEntry, int skipBytes, boolean hasDelimByte) {
		return binarySearch(target, ch, 0, maxEntry, skipBytes, hasDelimByte);
	}
	
	public static byte [] ezLoad(FileChannel ch, int len) throws IOException {
		ByteBuffer buffy = ByteBuffer.allocate(len);
		if(ezLoad(ch, buffy)) {
			return buffy.array();
		}
		return null;
	}
	
	public static boolean ezLoad(FileChannel ch, ByteBuffer buffy) throws IOException {
		while(buffy.hasRemaining()) {
			int amt = ch.read(buffy);
			if(amt == -1) {
				return false;
			}
		}
		return true;
	}
	
	public static boolean ezLoad(InputStream is, byte [] buffy, int offsetIntoBuf, int max) throws IOException {
		int rez = -1;
		if(max > buffy.length) {
			max = buffy.length;
		}
		int x = 0;
		while(x < max) {
			rez = is.read(buffy, x + offsetIntoBuf, (max - x));
			if(rez == -1) {
				break;
			}
			x+= rez;
		}
		if(rez == -1) return false;
		return true;
	}
	
	public static boolean ezLoad(InputStream is, byte [] buffy, int max) throws IOException {
		return ezLoad(is, buffy, 0, max);
	}
	
	
	public static void ezStore(FileChannel ch, byte[] store) throws IOException {
		ByteBuffer buffy = ByteBuffer.wrap(store);
		ezStore(ch, buffy);
	}
	
	public static void ezStore(FileChannel ch, ByteBuffer slayer) throws IOException{
		while(slayer.hasRemaining()) 
			ch.write(slayer);
	}
	
	/**
	 * Finds items in a dir
	 * @param path the path to search in
	 * @param criteria the criteria (case-insenstive) to match against
	 * @return
	 */
	public static List<String> listFiles(String path, String criteria) {
		Set<String> list = new HashSet<String>();
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
	            Paths.get(path), criteria.toUpperCase())) {
	        dirStream.iterator().forEachRemaining( p -> list.add(p.toString()));
		} catch(IOException ioe) {
			throw new CloudException(ioe);		    
		}
		
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
	            Paths.get(path), criteria.toLowerCase())) {
	        dirStream.iterator().forEachRemaining( p -> list.add(p.toString()));
		} catch(IOException ioe) {
			throw new CloudException(ioe);		    
		}
		return new ArrayList<String>(list);
	}
	
	
	public static boolean createDirIfNeeded(String path) {
		File f = new File(path);
		if(f.exists() == false) {
			if(f.mkdir() == false) {
				throw new CloudException("Dir creation at " + f.getPath() + " failed");
			}
			return true;
		}
		return false;
	}
	
	public static void fileAppend(String source, String dest) {
		try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(dest, true)); FileInputStream fis = new FileInputStream(source)) {
			byte [] bites = new byte[FILE_BUFFER_SIZE];
			while(true) {
				int amt = fis.read(bites);
				if(amt == -1) {
					break;
				}
				fos.write(bites, 0, amt);
			}
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static <T extends ByteStruct<T>> T binarySearch(T target, FileChannel ch, int l, int r, int skipBytes, boolean hasDelimByte) {
		int recordSizeReal = target.recordSize();
		if(hasDelimByte) {
			recordSizeReal +=1;
		}
		try {
			// Linsanity check
			if(ch.size() < recordSizeReal) {
				return null;
			}
			if(l > r) {
				return null;
			}
			int m = (l + r) / 2;
			int sutra = ((m) * recordSizeReal) + skipBytes;
			ch.position(sutra);
			byte [] bites = ezLoad(ch, target.recordSize());
			if(bites == null) {
				throw new IOException("Premature end of stream at position " + ch.position() 
						+ " with l of " + l + " and r of " + r + "; recordSize is " + recordSizeReal
						+ " and total stream length is " + ch.size());
			}
		
			Class clazz = target.getClass();
			T swap = (T) clazz.newInstance();
			
			swap.load(bites);
			int dareTo = target.compareTo(swap);
			if(dareTo == 0) {
				return swap;
			}
			if(dareTo < 0) {
				r = m-1;
			} else {
				l = m +1;
			}
			return binarySearch(target, ch, l, r, skipBytes, hasDelimByte);
		} catch(Exception e) {
			throw new CloudException(e);
		}
	}
	
	
}
