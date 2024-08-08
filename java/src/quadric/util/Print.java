package quadric.util;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.crypt.Crc32c;
import quadric.crypt.CryptUtil;

public class Print implements ByteStruct<Print>, Comparable<Print> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger( Print.class.getName() );
	
	public static final int PRINT_SIZE = 16;

		public byte [] bytes = null;
		
		public Print() {
			bytes = new byte[PRINT_SIZE];
		}
		
		public Print(String longKey) {
			bytes = CryptUtil.hexToBytes(longKey);
			if(bytes.length != PRINT_SIZE) {
				throw new CloudException("Improper Print size");
			}
		}
		
		public Print(byte [] bites) {
			this.bytes = bites;
			if(bites.length < PRINT_SIZE) {
				throw new CloudException("Improper Print size");
			}
		}
		
		
		public int hashCode() {
			Crc32c crc = new  Crc32c();
			crc.update(bytes, 0, PRINT_SIZE);
			return (int) crc.getValue();
		}
		
		public boolean equals(Object o2) {
			Print cord = (Print) o2;
			return Arrays.equals(this.bytes, cord.bytes);
		}
		
		public String toString() {
			return CryptUtil.bytesToHex(bytes);
		}
		
		public int compareTo(Print o2) {
			//LOGGER.debug( this.toString() + " compareTo " + o2.toString() + " will return " + this.toString().compareTo(o2.toString()));
			return this.toString().compareTo(o2.toString());
		}

		@Override
		public void load(byte[] bites) {
			if(bites.length < PRINT_SIZE) {
				throw new CloudException("Wrong print size");
			}
			bytes = bites;
			
		}

		@Override
		public byte[] store() {
			return bytes;
		}

		@Override
		public int recordSize() {
			return PRINT_SIZE;
		}

		public boolean isBlank() {
			return BlankBlocks.instance().isBlank(toString());
		}
		


}
