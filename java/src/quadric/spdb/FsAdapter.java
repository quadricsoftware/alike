package quadric.spdb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.A2Share;
import quadric.blockvaulter.CloudAdapter;
import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.GetResult;
import quadric.blockvaulter.VaultSettings;
import quadric.util.Pair;
import quadric.util.Print;
import quadric.util.SeeThruByteArrayInputStream;
import quadric.util.VaultUtil;

public abstract class FsAdapter extends CloudAdapter {
	private static final Logger LOGGER = LoggerFactory.getLogger( FsAdapter.class.getName() );
	
	protected String base;
	protected SimpleAdapter deth;
	protected String bloxPath;
	protected String journoPath;
	protected boolean paranoid;
	protected volatile boolean prepopComplete = false;
	
	public FsAdapter(int dsNumReal, boolean skipTailMd5, boolean useLocalTempForPuts) {
		super(dsNumReal);
		paranoid = VaultSettings.instance().isParanoid();
		A2Share share = VaultSettings.instance().getShareConfig(dsNumReal);
		base = share.getLocalPath();
		if(base.endsWith("\\") || base.endsWith("/")) {
			base = base.substring(0, base.length() -1);
		}
		LOGGER.debug("Base path will be " + base);
		deth = new SimpleAdapter(base, skipTailMd5, useLocalTempForPuts, dsNumReal);
		bloxPath = base + File.separator + "blocks";
		journoPath = base + File.separator + "journals";
		sanityCheck();
		if(isReadOnly() == false) {
			File d = new File(journoPath);
			if(d.exists() == false) {
				d.mkdir();
			}
			d = new File(bloxPath);
			if(d.exists() == false) {
				d.mkdir();
			}
			Thread t = new Thread( () ->  {
				prepop(bloxPath);
				prepopComplete = true;
			});
			t.start();
		} else {
			LOGGER.debug("Adapter initializing READ-ONLY");
			prepopComplete =  true;
		}
		LOGGER.info("Adapter at path " + base + " now initialized");
		
	}
	
	public abstract void prepop(String blockPath);
	public abstract String getBlockPath(String name);
	
	protected void sanityCheck() { ; }
	
	protected Pair<Boolean,String> getPather(String name) {
		// Is it a block?
		if(name.length() == 32 && name.indexOf('.') == -1) {
			return new Pair<Boolean,String>(true, getBlockPath(name));
		}
		String journalsDir = "";
		if(Character.isDigit(name.charAt(0))) {
			journalsDir = "journals" + File.separator;
		}
		return new Pair<Boolean,String>(false, journalsDir + name);
	}
	
	
	protected String getPath(String name) {
		return getPather(name).second;
		
	}
	
	@Override
	public String getBlockPath(Print p) {
		Pair<Boolean,String> nicePair = getPather(p.toString());
		return nicePair.second;
	}
	
	@Override
	public boolean cantVerifyKurganBlocks() {
		return true;
	}
	
	@Override
	public boolean putBlock(String path, InputStream in, long len, String dr5) {
		if(isReadOnly()) {
			throw new CloudException("Adapter set to read-only mode");
		}
		Pair<Boolean,String> nicePair = getPather(path);
		// Use md5 checks on all journal/metadata puts
		if(nicePair.first == false) {
			deth.putBlock(nicePair.second, in, len, dr5);
			return true;
		}
		if(prepopComplete == false) throw new CloudNotReadyException("Block directory generation not yet complete");
		// On all other puts, we can just suck in the bytes
		byte [] bites;
		// SeeThruByteArray is an alluring way to save I/O costs...
		if(in instanceof SeeThruByteArrayInputStream) {
			bites = ((SeeThruByteArrayInputStream) in).reveal();
		} else {
			bites = new byte[(int) len];
			try {
				VaultUtil.ezLoad(in, bites, bites.length);
			} catch(Exception e) {
				throw new SpdbException(e);
			}
		}
		boolean performedWrite = false;
		// Wait for it...
		try (FileOutputStream buf = new FileOutputStream(nicePair.second)) {
			buf.write(bites, 0, (int) len);
			performedWrite = true;
		} catch(FileAlreadyExistsException faap) {
			// This is "safe" to eat mmm... yummi
		} catch(IOException ioe) {
			throw new CloudException(ioe);
		}
		if(paranoid) {
			GetResult gs = getBlock(path, 0);
			KurganBlock kb;
			try {
				kb = KurganBlock.create(gs, path, true);
			} catch (IOException e) {
				throw new CloudException("Validation of put at " + path + " failed with error", e);
			}
			if(kb.getMd5().equals(path) == false) {
				throw new CloudException("Validation of block " + path + " failed");
			}
		}
		return performedWrite;
	}

	@Override
	public GetResult getBlock(String path, long max) {
		// If this is a journal or metadata file, check it with md5
		Pair<Boolean,String> nicePair = getPather(path);
		if(nicePair.first == false) {
			return deth.getBlock(nicePair.second, max);
		}
		if(prepopComplete == false) throw new CloudNotReadyException("Block directory generation not yet complete");
		// Juts get it off the FS direct
		GetResult getty = new GetResult();
		try {
			getty.in = new FileInputStream(nicePair.second);
		} catch (FileNotFoundException e) {
			throw new CloudException(e);
		}
		getty.len = (new File(nicePair.second)).length();
		if(getty.len == 0) {
			LOGGER.error("Found zero-sized block at path: " + nicePair.second);
		}
		if(max != 0 && getty.len > max) {
			getty.len = max;
		}
		return getty;
	}

	@Override
	public boolean stat(String path) {
		Pair<Boolean,String> nicePair = getPather(path);		
		if(nicePair.first == false) {
			return deth.stat(nicePair.second);
		}
		if(prepopComplete == false) throw new CloudNotReadyException("Block directory generation not yet complete");
		return new File(nicePair.second).exists();
	}

	@Override
	public void del(String path) {
		if(isReadOnly()) {
			throw new CloudException("Adapter set to read-only mode");
		}
		Pair<Boolean,String> nicePair = getPather(path);
		if(nicePair.first == false) {
			deth.del(getPath(path));
			return;
		} 
		if(prepopComplete == false) throw new CloudNotReadyException("Block directory generation not yet complete");
		new File(nicePair.second).delete();
	}

	@Override
	public String id(String path) {
		Pair<Boolean,String> nicePair = getPather(path);
		if(nicePair.first == false) {
			return deth.id(getPath(path));
		} 
		throw new IllegalArgumentException("Op not supported");
		
	}
	
	

}
