package quadric.ods;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import quadric.blockvaulter.CloudException;
import quadric.util.ByteStruct;
import quadric.util.Print;
import quadric.util.VaultUtil;

public class KurganAmbHeader implements ByteStruct<KurganAmbHeader> {
	private Print p;
	private int blockSize;
	
	
	@Override
	public int compareTo(KurganAmbHeader arg0) {
		return p.compareTo(arg0.p);
	}

	@Override
	public void load(byte[] bites) {
		ByteBuffer buffy = ByteBuffer.wrap(bites);
		buffy.order(ByteOrder.LITTLE_ENDIAN);
		try {
			p = new Print(new String(buffy.array(), 4, 32, "US-ASCII"));
		} catch (UnsupportedEncodingException e) {
			throw new CloudException(e);	
		}
		buffy.position(0);
		blockSize = buffy.getInt();
		
	}

	@Override
	public byte[] store() {
		return null;
	}

	@Override
	public int recordSize() {
		return 40;
	}

	public Print getPrint() {
		return p;
	}

	public void setPrint(Print p) {
		this.p = p;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public void setBlockSize(int blockSize) {
		this.blockSize = blockSize;
	}

	@Override
	public String toString() {
		return "KurganAmbHeader [p=" + p + ", blockSize=" + blockSize + "]";
	}

	
	
	
}
