package quadric.legacy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.ods.VmVersion;
import quadric.ods.dao.Crud;

/**
 * Represents a NimbusDB vm_version object and its vm_files
 *
 */
public class NimbusVersionCrud extends Crud<NimbusVersion> {
	private static final Logger LOGGER = LoggerFactory.getLogger( NimbusVersionCrud.class.getName() );

	public NimbusVersionCrud(Connection con) {
		super(con);
	}

	public NimbusVersionCrud() {
		;
	}

	@Override
	public void tableCreateIfNeeded() {
		String create1 = "CREATE TABLE IF NOT EXISTS VM_Version (VMID INTEGER, VMVersion INTEGER, vmType INTEGER, size INTEGER, timestamp INTEGER, committed int, metaInfo VARCHAR, action INTEGER, processingTime INTEGER, jobID int )";
		String create2 ="CREATE TABLE IF NOT EXISTS VM_Files (VMID INTEGER, VMVersion INTEGER, filename VARCHAR, fileVersion DOUBLE, fileSize INTEGER, timestamp INTEGER, committed int, deltaSize INTEGER, deltaPostDedup INTEGER )";
		String create3 = "CREATE TABLE IF NOT EXISTS version_site (VMID INTEGER, timestamp INTEGER, siteid INTEGER)";
		String create4 = "CREATE UNIQUE INDEX IF NOT EXISTS version_site_i ON version_site (VMID, timestamp, siteId)";
		try {			
			Statement s = con.createStatement();
			s.execute(create1);
			s.execute(create2);
			s.execute(create3);
			s.execute(create4);
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
		
	}

	@Override
	public void create(NimbusVersion v) {
		//INSERT OR REPLACE INTO VM_Version (VMID, VMVersion, vmType, size, timestamp, committed, metaInfo, action,processingTime, jobID) VALUES (?,?,?,?,?,?,?,?,?,?)
		
		/* Committed: 
		 internalUse = -1,
       	committed = 1,
       	uncommitted = 2,
       	vaultComplete = 6,
       	purgedOnlyOnsite = -2,
			 */
		
		try {
			
			PreparedStatement s2 = con.prepareStatement("SELECT max(vmVersion) FROM vm_version WHERE vmid=?");
			s2.setLong(1, v.getVmId());
			s2.execute();
			int maxVersion = 0;
			ResultSet res = s2.getResultSet();
			if(res.next()) {
				maxVersion = res.getInt(1);
			}
			final int myMaxVersion = maxVersion +1;
			
				PreparedStatement s = con.prepareStatement("INSERT INTO vm_version (VMID, VMVersion, vmType, "
						+ "size, timestamp, committed, metaInfo, action,processingTime, jobID) "
						+ "VALUES (?,?,?,?,?,?,?,?,?,?)");
				s.setLong(1, v.getVmId());
				s.setInt(2, myMaxVersion);
				s.setInt(3, v.getVirtualType());
				s.setLong(4,  v.getTotalSize());
				s.setLong(5,  v.getVersion());
				// Committed
				s.setInt(6,  1);
				// Skippy CHUNKY PB&Death  
				//ByteArrayInputStream blowChunks = null;
				try {
					//blowChunks = new ByteArrayInputStream(v.getMetaData().getBytes("US-ASCII"));
					s.setBytes(7, v.getMetaData().getBytes("US-ASCII"));
				} catch(UnsupportedEncodingException usee) {
					throw new SQLException(usee);
				}
				
				//s.setBlob(7, blowChunks);
				// Action
				s.setInt(8, 0);
				// processing time
				long processingTime = System.currentTimeMillis() - v.getVersion();
				s.setLong(9,  processingTime);
				s.setLong(10, v.getJobId());
				s.execute();
			
			
			int offset = 0;
			for(Long l : v.getDiskSizes()) {
				final int finalOffset = offset++;
				
					 s = con.prepareStatement("INSERT INTO VM_Files"
													+ "	(VMID, VMVersion, filename, fileVersion, fileSize, timestamp,committed,deltaSize,deltaPostDedup)"
													+ " VALUES (?,?,?,?,?,?,2,0,0)");
					s.setLong(1, v.getVmId());
					s.setInt(2, myMaxVersion);
					String fileName = "" + finalOffset;
					s.setString(3, fileName);
					s.setInt(4, myMaxVersion);
					s.setLong(5, l);
					s.setLong(6, v.getVersion());
					// Committed
					//s.setLong(7, 1);
					LOGGER.trace("About to execute VM_Files insert...");
					s.execute();
				
			}
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	/**
	 * This is really never needed....
	 */
	@Override
	public void update(NimbusVersion v) {
		throw new CloudException("Not implemented, and for good reason");
		/*try {
			PreparedStatement s = con.prepareStatement("UPDATE vm_version SET committed=? WHERE vmid=? AND timestamp=?");
			s.setLong(1, v.getVmId());
			s.setLong(2,  v.getVersion());
		} catch (SQLException e) {
			throw new CloudException(e);
		}*/
	}
	
	public void updateVmFullSize(NimbusVersion v, int diskNum, long largoSize) {
		try {
			
				PreparedStatement s = con.prepareStatement("UPDATE vm_files SET fileSize=? WHERE vmid=? AND timestamp=? AND filename=?");
				s.setLong(1, largoSize);
				s.setLong(2, v.getVmId());
				s.setLong(3,  v.getVersion());
				s.setInt(4, diskNum);
				s.execute();
			
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public void updateVmDeltaSize(NimbusVersion v, int diskNum, long delta) {
		try {
			
				PreparedStatement s = con.prepareStatement("UPDATE vm_files SET deltaSize=? WHERE vmid=? AND timestamp=? AND filename=?");
				s.setLong(1, delta);
				s.setLong(2, v.getVmId());
				s.setLong(3,  v.getVersion());
				s.setInt(4, diskNum);
				s.execute();
			
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}
	
	
	public void updateJobOriginalSize(int jobId, long originalSize) {
		try {
				PreparedStatement s = con.prepareStatement("UPDATE job SET originalSize=? WHERE jobID=?");
				s.setLong(1, originalSize);
				s.setInt(2, jobId);
				s.execute();
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public void updateJobDelta(int jobId, long sz) {
		try {
			
				PreparedStatement s = con.prepareStatement("UPDATE job SET sizeOnDisk=?, sizeSent=? WHERE jobID=?");
				s.setLong(1, sz);
				s.setLong(2, sz);
				s.setInt(3, jobId);
				s.execute();
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}
	
	public void updateVmPostDedupSize(NimbusVersion v, int diskNum, long post) {
		try {
			
				PreparedStatement s = con.prepareStatement("UPDATE vm_files SET deltaPostDedup=? WHERE vmid=? AND timestamp=? AND filename=?");
				s.setLong(1, post);
				s.setLong(2, v.getVmId());
				s.setLong(3,  v.getVersion());
				s.setInt(4, diskNum);
				s.execute();
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}

	@Override
	public void delete(NimbusVersion v) {
		try {
			
				PreparedStatement s = con.prepareStatement("DELETE FROM vm_version v WHERE vmid=? AND timestamp=?");
				s.setLong(1, v.getVmId());
				s.setLong(2,  v.getVersion());
				s.execute();
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public NimbusVersion toObject(ResultSet... r) {
		// CREATE TABLE VM_Version (VMID INTEGER, 
		// VMVersion INTEGER, vmType INTEGER, size INTEGER, timestamp INTEGER, 
		// committed int, metaInfo VARCHAR, action INTEGER, processingTime INTEGER, jobID int )
		try {
			ResultSet rs1 = r[0];
			//ResultSet rs2 = r[1];
			
			NimbusVersion nb = new NimbusVersion();
			nb.setVmId(rs1.getLong(1));
			nb.setVirtualType(rs1.getInt(3));
			nb.setVersion(rs1.getLong(5));
			nb.setMetaData(rs1.getString(7));
			nb.setJobId(rs1.getLong(10));
			
			/* do {
				nb.getDiskSizes().add(rs2.getLong(1));
			} while(rs2.next());*/
			
			return nb;
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	
		
	}

}
