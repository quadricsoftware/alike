package quadric.legacy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import quadric.blockvaulter.CloudException;
import quadric.ods.dao.Crud;

/**
 * Represents a version_site instance
 *
 */
public class NimbusSiteCrud extends Crud<NimbusSite> {

	public NimbusSiteCrud(Connection con) {
		super(con);
	}

	@Override
	public void tableCreateIfNeeded() {
		;
		
	}

	@Override
	public void create(NimbusSite v) {
		try {
			
				PreparedStatement s = con.prepareStatement("INSERT INTO version_site VALUES (?,?,?)");
				s.setLong(1, v.getVmId());
				s.setLong(2,  v.getVersion());
				s.setInt(3, v.getSiteId());
				s.execute();
			
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public void update(NimbusSite v) {
		//
		
	}

	@Override
	public void delete(NimbusSite v) {
		try {
			
				PreparedStatement s = con.prepareStatement("DELETE FROM version_site WHERE vmid=? AND timestamp=? AND siteId=?");
				s.setLong(1, v.getVmId());
				s.setLong(2,  v.getVersion());
				s.setInt(3, v.getSiteId());
				s.execute();
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public NimbusSite toObject(ResultSet... r) {
		try {
			ResultSet rs = r[0];
			NimbusSite site = new NimbusSite();
			site.setVmId(rs.getLong(1));
			site.setVersion(rs.getLong(2));
			site.setSiteId(rs.getInt(3));
			return site;
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}

}
