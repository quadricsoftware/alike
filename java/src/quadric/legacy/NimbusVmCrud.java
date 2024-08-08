package quadric.legacy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import quadric.blockvaulter.CloudException;
import quadric.ods.VmVersion;
import quadric.ods.dao.*;

public class NimbusVmCrud extends Crud<VmVersion> {

	public NimbusVmCrud(Connection con) {
		super(con);
	}

	public NimbusVmCrud() {
	}

	/**
	 * Technically we don't need this but it's convenient to have in case
	 */
	@Override
	public void tableCreateIfNeeded() {
		try {
			String wad = "CREATE TABLE IF NOT EXISTS VM (VMID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, VMName VARCHAR, virtTech INTEGER, hostID NOT NULL, deleteFlag INTEGER, maxVersions INTEGER, UUID VARCHAR NOT NULL, poolID VARCHAR NOT NULL, maxVersionsOffsite INTEGER, authProfile INTEGER, accessIP VARCHAR)";
			Statement s = con.createStatement();
			s.execute(wad);
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public void create(VmVersion v) {
		try {
			
			// INSERT or REPLACE INTO VM (VMNAME,VirtTech,hostID,maxVersions,UUID,poolID, maxVersionsOffsite,authProfile, accessIP) VALUES (?,?,?,?,?,?,?,0,'')
			PreparedStatement s = con.prepareStatement("INSERT INTO vm (VMNAME,VirtTech,hostID,maxVersions,UUID,poolID,"
														+ " maxVersionsOffsite,authProfile, accessIP) VALUES (?,?,0,0,?,'',0,0,'')");
			s.setObject(1, v.getVmName());
			s.setInt(2, v.getVirtualType());
			s.setString(3, v.getPlatformStyleUuid());
			s.execute();
			lastInsertId = s.getGeneratedKeys().getLong(1);
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public void update(VmVersion v) {
		;
		
	}
	
	public void updateMaxVersions(VmVersion v, int maxOnsite) {
		try {
			
			// INSERT or REPLACE INTO VM (VMNAME,VirtTech,hostID,maxVersions,UUID,poolID, maxVersionsOffsite,authProfile, accessIP) VALUES (?,?,?,?,?,?,?,0,'')
			PreparedStatement s = con.prepareStatement("UPDATE vm SET maxVersions=? WHERE uuid=?");
			s.setInt(1, maxOnsite);
			s.setString(2, v.getPlatformStyleUuid());
			s.execute();
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}

	@Override
	public void delete(VmVersion v) {
		try {
			PreparedStatement s = con.prepareStatement("DELETE FROM vm WHERE UUID=?");
			s.setString(1, v.getPlatformStyleUuid());
			s.execute();
			
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public VmVersion toObject(ResultSet... r) {
		return null;
	}
	
}
