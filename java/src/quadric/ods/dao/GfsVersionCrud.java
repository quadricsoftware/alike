package quadric.ods.dao;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import quadric.blockvaulter.A2Share;
import quadric.blockvaulter.CloudException;
import quadric.gfs.GfsVersion;
import quadric.util.VaultUtil;

/**
 *
 */
public class GfsVersionCrud extends Crud<GfsVersion> {
	
	public GfsVersionCrud() {
		super();
	}
	
	public GfsVersionCrud(Connection con) {
		super(con);
	}


	@Override
	public void tableCreateIfNeeded() {
		String create = "CREATE TABLE IF NOT EXISTS gfs_version (scheduleId INTEGER, installId VARCHAR, siteId INTEGER, epoch INTEGER, uuid VARCHAR)";
		String create2 = "CREATE UNIQUE INDEX IF NOT EXISTS gfs_version_index ON gfs_version (epoch, uuid, siteId)";
		
		String create3 = "CREATE TABLE IF NOT EXISTS gfs (gfsId INTEGER PRIMARY KEY, name VARCHAR UNIQUE)";
		String create4 =  "CREATE TABLE IF NOT EXISTS gfs_instance (gfsId INTEGER NOT NULL, card INTEGER, versions INTEGER, policy INTEGER)";
		String create5 =  "CREATE TABLE IF NOT EXISTS gfs_schedule (gfsId INTEGER NOT NULL, scheduleId INTEGER, installId VARCHAR, siteId INTEGER)";
		String create6 =  "CREATE UNIQUE INDEX IF NOT EXISTS gfs_schedule_index ON gfs_schedule (scheduleId, installId, siteId, gfsId)";
		String create7 =  "CREATE UNIQUE INDEX IF NOT EXISTS gfs_instance_index ON gfs_instance (gfsId, policy)";
		
		
		try {
			Statement state = con.createStatement();
			state.execute(create);
			state.execute(create2);
			state.execute(create3);
			state.execute(create4);
			state.execute(create5);
			state.execute(create6);
			state.execute(create7);

		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}
		
	
	@Override
	public void create(GfsVersion v) {
		try {
			PreparedStatement st = con.prepareStatement("INSERT INTO gfs_version VALUES(?,?,?,?,?)");
			st.setInt(1, v.getScheduleId());
			st.setString(2, v.getInstallId());
			st.setInt(3, v.getSiteId());
			st.setLong(4, v.getEpoch());
			st.setString(5, v.getUuuid());
			st.execute();
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}
	
	@Override
	public void update(GfsVersion v) {
		throw new IllegalStateException("Not implemented");
		
	}
	
	@Override
	public void delete(GfsVersion v) {
		try {
			PreparedStatement st = con.prepareStatement("DELETE FROM gfs_version WHERE siteId=? AND epoch=? AND uuid=?");
			st.setInt(1, v.getSiteId());
			st.setLong(2, v.getEpoch());
			st.setString(3, v.getUuuid());
			st.execute();
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}
	
	@Override
	public GfsVersion toObject(ResultSet... multi) {
		ResultSet r = multi[0];
		try {
			GfsVersion version = new GfsVersion();
			version.setScheduleId(r.getInt(1));
			version.setInstallId(r.getString(2));
			version.setSiteId(r.getInt(3));
			version.setEpoch(r.getLong(4));
			version.setUuuid(r.getString(5));
			return version;
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}
}
