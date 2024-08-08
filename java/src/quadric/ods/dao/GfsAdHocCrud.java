package quadric.ods.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import quadric.blockvaulter.CloudException;
import quadric.gfs.GfsAdHoc;

public class GfsAdHocCrud extends Crud<GfsAdHoc> {

	public GfsAdHocCrud(Connection con) {
		super(con);
	}
	
	@Override
	public void tableCreateIfNeeded() {
		String create8 = "CREATE TABLE IF NOT EXISTS gfs_adhoc (uuid VARCHAR, siteId INTEGER, quantity INTEGER)";
		String create9 = "CREATE UNIQUE INDEX IF NOT EXISTS gfs_adhoc_index ON gfs_adhoc (uuid, siteId)";
		
		try {
			Statement state = con.createStatement();
			state.execute(create8);
			state.execute(create9);
		} catch (SQLException e) {
			throw new CloudException(e);
		}

		
	}

	@Override
	public void create(GfsAdHoc v) {
		try {
			PreparedStatement st = con.prepareStatement("INSERT INTO gfs_adhoc VALUES(?,?,?)");
			st.setString(1, v.getUuid());
			st.setInt(2, v.getSiteId());
			st.setInt(3, v.getQuantity());
			st.execute();
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public void update(GfsAdHoc v) {
		try {
			PreparedStatement st = con.prepareStatement("UPDATE gfs_adhoc SET quantity=? WHERE uuid=? AND siteId=?");
			st.setInt(1, v.getQuantity());
			st.setString(2, v.getUuid());
			st.setInt(3, v.getSiteId());
			st.execute();
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public void delete(GfsAdHoc v) {
		try {
			PreparedStatement st = con.prepareStatement("DELETEF FROM gfs_adhoc WHERE uuid=? AND siteId=?");
			st.setString(1, v.getUuid());
			st.setInt(2, v.getSiteId());
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public GfsAdHoc toObject(ResultSet... r) {
		try {
			ResultSet rs = r[0];
			GfsAdHoc kill = new GfsAdHoc();
			kill.setUuid(rs.getString(1));
			kill.setSiteId(rs.getInt(2));
			kill.setQuantity(rs.getInt(3));
					
			return kill;
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}
	
}
