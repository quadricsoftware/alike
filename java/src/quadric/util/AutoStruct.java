package quadric.util;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import quadric.blockvaulter.CloudException;
import quadric.crypt.Crc32c;

import java.util.Objects;
import java.util.TreeMap;



/**
 * Represents a dynamic supertype of c-style structs that use reflection to load() and store() their values
 * 
 * Supports only long, integer, short, byte, byte[], and composition 
 *
 */
@SuppressWarnings("rawtypes")
public class AutoStruct implements ByteStruct<AutoStruct> {
	//private static final Logger LOGGER = LoggerFactory.getLogger( AutoStruct.class.getName() );
	
	private static Object lock = new Object();
	private static Map<String,Integer> recordSizes = new HashMap<String,Integer>();
	private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
	
	int recordSize = 0;

	public void setBigEndian() {
		byteOrder = ByteOrder.BIG_ENDIAN;
	}
	
	@Override
	public int compareTo(AutoStruct arg0) {
		return new String(store()).compareTo(new String(arg0.store()));
	}
	
	@Override
	public void load(byte[] bites) {
		if(bites.length < recordSize()) {
			throw new CloudException("Not enough bytes to fit struct");
		}
		ByteBuffer buffy = ByteBuffer.wrap(bites);
		buffy.order(byteOrder);
		for(Method man : getPutters()) {
			assignSetter(man, buffy);
		}
		
	}

	@Override
	public byte[] store() {
		int mySize = recordSize();
		ByteBuffer buffy = ByteBuffer.allocate(mySize);
		buffy.order(byteOrder);
		List<Object> vals = beanProperties();
		for(Object o : vals) {
			append(buffy, o);
		}
		return buffy.array();
	}

	@Override
	public int recordSize() {
		synchronized(lock) {
			if(recordSize == 0) {
				Integer tg = recordSizes.get(this.getClass().getName());
				if(tg == null) {
					List<Object> fields = beanProperties();
					for(Object o : fields) {
						int szy = getSize(o);
						//LOGGER.trace("Object " + this + " field " + o + " of size " + szy);
						recordSize += szy;
					}
					recordSizes.put(this.getClass().getName(), recordSize);
				} else {
					recordSize = tg;
				}
			}
			return recordSize;
		}
	}
	
	public static int checksum(byte [] incoming) {
		// Calculate checksum
		Crc32c craphead = new Crc32c();
		craphead.update(incoming, 0, incoming.length);
		byte[] cruftty = craphead.getValueAsBytes();
		ByteBuffer smarmy = ByteBuffer.wrap(cruftty);
		return smarmy.getInt();
		
	}
	
	public static int onesComplementChecksum(byte [] incoming) {
		int wad = 0;
		for(byte b : incoming) {
			wad += Byte.toUnsignedInt(b);
		}
		return ~wad;
	}
	
	public int onesComplementChecksum() {
		byte [] awesomeCruft = this.store();
		return onesComplementChecksum(awesomeCruft);
	}
	
	public int checksum() {
		// Calculate checksum
		byte [] awesomeCruft = this.store();
		return checksum(awesomeCruft);
	}
	
	private int getSize(Object o) {
		
		if(Short.class.isInstance(o))
			return 2;
		if(Integer.class.isInstance(o))
			return 4;
		if(Long.class.isInstance(o)) 
			return 8;
		if(o instanceof byte [])
			return ((byte[]) o).length;
		if(o instanceof Byte) 
			return 1;
		if(o instanceof ByteStruct)
			return ((ByteStruct) o).recordSize();
		throw new IllegalArgumentException("Unknown type");
	}
	
	private void append(ByteBuffer buffy, Object o) {
		if(o instanceof Short) 
			buffy.putShort((Short)o);
		else if(o instanceof Integer)
			buffy.putInt((Integer) o);
		else if(o instanceof Long) 
			buffy.putLong((Long) o);
		else if(o instanceof byte [])
			buffy.put((byte []) o);
		else if(o instanceof Byte) 
			buffy.put((byte) o);
		else if(o instanceof ByteStruct)
			buffy.put(((ByteStruct) o).store());
		else 
			throw new CloudException("Unknown type " + o.getClass().getName());
	}
	
	
	private void assignSetter(Method man, ByteBuffer buffy) {
		try {
			Class clazz = man.getParameterTypes()[0];
			if(clazz.equals(Short.TYPE)) {
				man.invoke(this, buffy.getShort());
			} else if(clazz.equals(Integer.TYPE)) {
				man.invoke(this, buffy.getInt());
			} else if(clazz.equals(Long.TYPE)) {
				man.invoke(this, buffy.getLong());
			} else if(clazz.equals(byte[].class)) {
				byte [] dest;
				try {
					String name = man.getName().split("_")[1];
					int sizeDoesMatter = Integer.parseInt(name);
					dest = new byte[sizeDoesMatter];
					
				} catch(Exception e) {
					throw new CloudException("Method " + man.getName() + " does not have a _xxx appendage to specify its length");
				}
				buffy.get(dest);
				man.invoke(this, dest);
			} else if(clazz.equals(Byte.class)) {
				man.invoke(this, buffy.get());
			} else if(ByteStruct.class.isAssignableFrom(clazz)) {
				ByteStruct structly = (ByteStruct) clazz.newInstance();
				byte [] awesome = new byte[structly.recordSize()];
				buffy.get(awesome);
				structly.load(awesome);
				man.invoke(this, structly);
			} else {
				throw new CloudException("Unknown type");
			}
		} catch(Exception e) {
			throw new CloudException(e);
		}
		
	}
	
	private List<Method> getPutters() {
		try {
			Map<String,Method> vaguelyUseless =  Arrays.asList(Introspector.getBeanInfo(this.getClass(), Object.class).getPropertyDescriptors())
			.stream().filter(pd -> Objects.nonNull(pd.getWriteMethod()))
			.collect(Collectors.toMap( 
					PropertyDescriptor::getName,
					pd -> {
				try {
					return pd.getWriteMethod();
				} catch(Exception e) { 
					return null;
				}
			}));
			TreeMap<String,Method> sortMeGentlyWithAChainsaw = new TreeMap<String,Method>(vaguelyUseless);
			return new ArrayList<Method>(sortMeGentlyWithAChainsaw.values());
		} catch(IntrospectionException e) {
			return Collections.emptyList();
		}
		
	}
	
	private List<Object> beanProperties() {
		try {
			Map<String,Object> vaguelyUseless =  Arrays.asList(Introspector.getBeanInfo(this.getClass(), Object.class).getPropertyDescriptors())
			.stream().filter(pd -> Objects.nonNull(pd.getReadMethod()))
			.collect(Collectors.toMap( 
					PropertyDescriptor::getName,
					pd -> {
				try {
					return pd.getReadMethod().invoke(this);
				} catch(Exception e) { 
					return null;
				}
			}));
			TreeMap<String,Object> sortMeGentlyWithAChainsaw = new TreeMap<String,Object>(vaguelyUseless);
			/* if(LOGGER.isTraceEnabled()) {
				String allGuys = sortMeGentlyWithAChainsaw.keySet().stream().collect(Collectors.joining(", "));
				LOGGER.trace(allGuys);
			}*/
			
			return new ArrayList<Object>(sortMeGentlyWithAChainsaw.values());
		} catch(IntrospectionException e) {
			return Collections.emptyList();
		}
		
	}

}
