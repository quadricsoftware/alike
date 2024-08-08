package quadric.ods.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import quadric.blockvaulter.CloudException;
import quadric.ods.DsInfo;
import quadric.ods.MaintStats;
import quadric.util.Pair;



public class DsInfoCrud extends Crud<DsInfo> {
	
	public DsInfoCrud() { ; }
	public DsInfoCrud(Connection con) {
		super(con);
	}
	
	@Override
	public void tableCreateIfNeeded() {
		String create = "CREATE TABLE IF NOT EXISTS dsinfo (siteId INTEGER PRIMARY KEY, md5 VARCHAR, timestamp INTEGER, identifier VARCHAR, lastPurge INTEGER, purgeTime INTEGER, lastRecon INTEGER, reconTime INTEGER)";
	try {
		Statement state = con.createStatement();
		state.execute(create);
	} catch (SQLException e) {
		throw new CloudException(e);
	}
		
	}

	@Override
	public void create(DsInfo s) {
		try {
		
			PreparedStatement st = con.prepareStatement("INSERT INTO dsinfo VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
			st.setInt(1, s.getSiteId());
			st.setString(2,s.getLastTransactMd5());
			st.setLong(3,  s.getTimestamp());
			st.setString(4,  s.getIdentifier());
			MaintStats ms = s.getMaintStats();
			st.setLong(5,  ms.getLastPurge());
			st.setLong(6,  ms.getPurgeTime());
			st.setLong(7, ms.getLastRecon());
			st.setLong(8, ms.getReconTime());
			st.execute();
		} catch(Exception e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public void update(DsInfo s) {
		try {
			
				PreparedStatement st = con.prepareStatement("UPDATE dsinfo SET identifier=?,lastPurge=?,purgeTime=?,lastRecon=?,reconTime=? WHERE siteId=?");
				st.setString(1,  s.getIdentifier());
				MaintStats ms = s.getMaintStats();
				st.setLong(2,  ms.getLastPurge());
				st.setLong(3,  ms.getPurgeTime());
				st.setLong(4, ms.getLastRecon());
				st.setLong(5, ms.getReconTime());
				st.setInt(6, s.getSiteId());
				st.execute();
			
			} catch(Exception e) {
				throw new CloudException(e);
			}
		
	}

	@Override
	public void delete(DsInfo s) {
		try {
			
				PreparedStatement st = con.prepareStatement("DELETE FROM dsinfo WHERE siteId=?");
				st.setInt(1, s.getSiteId());
				st.execute();
			
		} catch(Exception e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public DsInfo toObject(ResultSet...multi) {
		ResultSet r = multi[0];
		ResultSet r1 = multi[1];
		ResultSet r2 = multi[2];
		try {
			DsInfo ds = new DsInfo();
			ds.setSiteId(r.getInt(1));
			ds.setLastTransactMd5(r.getString(2));
			ds.setTimestamp(r.getLong(3));
			ds.setIdentifier(r.getString(4));
			MaintStats ms = ds.getMaintStats();
			ms.setLastPurge(r.getLong(5));
			ms.setPurgeTime(r.getLong(6));
			ms.setLastRecon(r.getLong(7));
			ms.setReconTime(r.getLong(8));
			
			ms.setJournalCount(r1.getInt(1));
			ms.setUnReconnedCount(r2.getInt(1));
			return ds;
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}

}
