package quadric.ods;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import quadric.blockvaulter.CloudException;
import quadric.crypt.CryptUtil;
import quadric.util.ByteStruct;
import quadric.vhdx.Guid;

public class EclHeader implements ByteStruct<EclHeader> {
	public static final int HEADER_SIZE = 48;
	long versionEpoch;
	Guid uuid;
	int hclRegionOffset;
	int metadataSize;
	int eclVersion;
	int diskCount;
	int vmNameSize;
	int virtualType;
	
	@Override
	public void load(byte[] bites) {
		if(bites.length != HEADER_SIZE) {
			throw new IllegalArgumentException("Wrong number of bytes");
		}
		ByteBuffer dbuf = ByteBuffer.wrap(bites);
		dbuf.order(ByteOrder.LITTLE_ENDIAN);
		versionEpoch = dbuf.getLong();
		byte [] uuidB = new byte[16];
		dbuf.get(uuidB);
		uuid = new Guid();
		uuid.load(uuidB);
		//uuid = CryptUtil.bytesToHex(uuidB);
		hclRegionOffset = dbuf.getInt();
		metadataSize = dbuf.getInt();
		eclVersion = dbuf.getInt();
		diskCount = dbuf.getInt();
		vmNameSize = dbuf.getInt();
		virtualType = dbuf.getInt();
	}
	
	@Override
	public byte[] store() {
		ByteBuffer dbuf = ByteBuffer.allocate(HEADER_SIZE);
		dbuf.order(ByteOrder.LITTLE_ENDIAN);
		dbuf.putLong(versionEpoch);
		byte [] uuidByte = uuid.store(); 
		//CryptUtil.hexToBytes(uuid);
		if(uuidByte.length != 16) {
			throw new CloudException("Uuid field illegal size");
		}
		dbuf.put(uuidByte);
		dbuf.putInt(hclRegionOffset);
		dbuf.putInt(metadataSize);
		dbuf.putInt(eclVersion);
		dbuf.putInt(diskCount);
		dbuf.putInt(vmNameSize);
		dbuf.putInt(virtualType);
		
		return dbuf.array();
	}
	

	@Override
	public int recordSize() {
		return HEADER_SIZE;
	}

	@Override
	public int compareTo(EclHeader arg0) {
		throw new CloudException("Not implemented");
	}
	 
}
