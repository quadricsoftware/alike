package quadric.ods.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.ods.VmVersion;

public class VmVersionCrud extends Crud<VmVersion> {
	private static final Logger LOGGER = LoggerFactory.getLogger( VmVersionCrud.class.getName() );

	public VmVersionCrud() {
		super();
	}
	
	public VmVersionCrud(Connection con) {
		super(con);
		
	}

	@Override
	public void create(VmVersion v) {
		try {
			
				PreparedStatement s = con.prepareStatement("INSERT INTO VVERSION VALUES(?,?,?,?,?,?,?)");
				s.setLong(1, v.getVaultId());
				s.setLong(2, v.getVersion());
				s.setString(3, v.getPlatformStyleUuid());
				s.setString(4, v.getVmName());
				s.setString(5,  v.getMetaData());
				s.setInt(6,  v.getVirtualType());
				s.setInt(7,  v.getSiteId());
				s.execute();
			
			
				
			for(Long guy : v.getDiskSizes()) {
				
					s = con.prepareStatement("INSERT INTO VVERSION_DISK VALUES(?,?,?)");
					s.setLong(1, v.getVaultId());
					s.setLong(2, guy);
					s.setInt(3,  v.getSiteId());
					s.execute();
				
			}
			
			
			
		} catch (SQLException e) {
			throw new CloudException("Failed to insert version " + v, e);
		}
		
	}

	@Override
	public void update(VmVersion v) {
		try {
			
				PreparedStatement s = con.prepareStatement("UPDATE VVERSION SET version=?,uuid=?,name=?,meta=?,virtType=? WHERE siteId=? AND flagId=?");
				s.setLong(1, v.getVersion());
				s.setString(2, v.getPlatformStyleUuid());
				s.setString(3, v.getVmName());
				s.setString(4,  v.getMetaData());
				s.setInt(5,  v.getSiteId());
				s.setInt(6, v.getVirtualType());
				s.setLong(7,  v.getVaultId());
				s.execute();
			
			// Clear any existing disks
			
				s = con.prepareStatement("DELETE FROM VVERSION_DISK WHERE versionId=?");
				s.setLong(1,  v.getVaultId());
				s.execute();
			
			// Recreate them
			for(Long guy : v.getDiskSizes()) {
				
					s = con.prepareStatement("INSERT INTO VVERSION_DISK VALUES(?,?,?)");
					s.setLong(1, v.getVaultId());
					s.setLong(2, guy);
					s.setInt(3, v.getSiteId());
					s.execute();
				
			}
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public void delete(VmVersion v) {
		try {
			
				PreparedStatement s = con.prepareStatement("DELETE FROM VVERSION WHERE siteId=? AND flagId=?");
				s.setInt(1, v.getSiteId());
				s.setLong(2, v.getVaultId());
				s.execute();
			
			// Cascade delete
			
				s = con.prepareStatement("DELETE FROM VVERSION_DISK WHERE versionId=? AND siteId=?");
				s.setLong(1, v.getVaultId());
				s.setInt(2,  v.getSiteId());
				s.execute();
			
		} catch (SQLException e) {
			throw new CloudException(e);
		} 
		
	}

	@Override
	public VmVersion toObject(ResultSet...multi) {
		VmVersion version = new VmVersion();
		ResultSet r = multi[0];
		try {
			version.setVaultId(r.getLong(1));
			version.setVersion(r.getLong(2));
			version.setPlatformStyleUuid(r.getString(3));
			version.setVmName(r.getString(4));
			version.setMetaData(r.getString(5));
			version.setVirtualType(r.getInt(6));
			version.setSiteId(r.getInt(7));
			ResultSet child = multi[1];
			do {
				long versionId = child.getLong(1);
				int siteId = child.getInt(3);
				if(versionId != version.getVaultId() || siteId != version.getSiteId()) {
					// Break out so we don't load someone else's cruft
					break;
				}
				long diskSize = child.getLong(2);
				/*int blocky = 524288;
				if(diskSize % blocky != 0) {
					LOGGER.info("Unusual disk geometry detected. Disk " + version.getVmName() + " of length " + (diskSize / 1024) 
										+ "KB is not divisible by our block size of " 
										+ (blocky / 1024) + "KB. Rounding it up.");
					// Add in a GB of padding for crufts
					//diskSize += (1000L * 1024L * 1024L);
					while(diskSize % blocky != 0) {
						diskSize++;
					}
				}*/
				version.getDiskSizes().add(diskSize);
				
				
			} while(child.next());
			
			return version;
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}

	@Override
	public void tableCreateIfNeeded() {
		String create = "CREATE TABLE IF NOT EXISTS vversion (flagId INTEGER, "
					+ "version VARCHAR, uuid VARCHAR COLLATE NOCASE, name VARCHAR, meta VARCHAR, virtType INTEGER, siteId INTEGER)";
		String index1 = "CREATE UNIQUE INDEX IF NOT EXISTS version_index ON VVERSION (version, uuid, siteId)";
		String index2 = "CREATE INDEX IF NOT EXISTS v_uuid_index ON VVERSION (uuid)";
		String index3 = "CREATE INDEX IF NOT EXISTS v_version_index ON VVERSION (version)";
		String index4 = "CREATE UNIQUE INDEX IF NOT EXISTS v_site_index ON VVERSION (flagId, siteId)";
		
		String create2 = "CREATE TABLE IF NOT EXISTS vversion_disk (versionId INTEGER, size INTEGER, siteId INTEGER)";
		String viewKill1 = "DROP VIEW IF EXISTS vversion_vaulted_v";
		String viewKill2 = "DROP VIEW IF EXISTS vversion_disk_vaulted_v";
		String view1 = "CREATE VIEW IF NOT EXISTS vversion_vaulted_v AS SELECT vv.* FROM vversion vv, flag f WHERE vv.flagId = f.txNo AND vv.siteId = f.siteId AND f.state > 2 AND f.txNo AND f.deletedBy is null";
		String view2 = "CREATE VIEW IF NOT EXISTS vversion_disk_vaulted_v AS SELECT vv.* FROM vversion_disk vv, flag f WHERE vv.versionId= f.txNo AND vv.siteId = f.siteId AND f.state > 2 AND f.deletedBy is null"; 
		
		String create3 = "CREATE TABLE IF NOT EXISTS both_tables (flagId INTEGER, uuid VARCHAR, timestamp INTEGER, siteid INTEGER)";
		Statement state;
		try {
			state = con.createStatement();
			state.execute(create);
			state.execute(index1);
			state.execute(index2);
			state.execute(index3);
			state.execute(index4);
			state.execute(create2);
			state.execute(viewKill1);
			state.execute(viewKill2);
			state.execute(view1);
			state.execute(view2);
			state.execute(create3);
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
		
	}
}
