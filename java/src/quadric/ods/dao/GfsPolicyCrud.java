package quadric.ods.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import quadric.blockvaulter.CloudException;
import quadric.gfs.Gfs;
import quadric.gfs.GfsPolicy;
import quadric.legacy.NimbusSite;

public class GfsPolicyCrud extends Crud<GfsPolicy> {

	public GfsPolicyCrud(Connection con) {
		super(con);
	}
	
	@Override
	public void tableCreateIfNeeded() {
		;
		
	}

	@Override
	public void create(GfsPolicy v) {
		;
		
	}

	@Override
	public void update(GfsPolicy v) {
		;
		
	}

	@Override
	public void delete(GfsPolicy v) {
		;
		
	}

	@Override
	public GfsPolicy toObject(ResultSet... r) {
		GfsPolicy pol = new GfsPolicy();
		try {
			ResultSet rs = r[0];
			
			pol.card = rs.getInt(1);
			pol.gfs = Gfs.fromInt(rs.getInt(2));
			pol.versions = rs.getInt(3);
			return pol;
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}
	
}
