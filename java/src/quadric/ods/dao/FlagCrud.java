package quadric.ods.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quadric.blockvaulter.CloudException;
import quadric.blockvaulter.VaultSettings;
import quadric.ods.EclFlags;
import quadric.ods.JournalManager;

public class FlagCrud extends Crud<EclFlags> {
	private static final Logger LOGGER = LoggerFactory.getLogger( FlagCrud.class.getName() );
	int errorRate = 0;
	Random rand;
	
	public FlagCrud() {
		super();
		init();
	}
	
	public FlagCrud(Connection con) { 
		super(con);
		init();
	}

	
	public void init() {
		errorRate = VaultSettings.instance().getForceHeaderErrorRate();
		if(errorRate != 0) {
			rand = new Random();
		}
	}
	
	public void forceErrorRandomly() {
		if(errorRate != 0) {
			if(rand.nextInt(errorRate) == 0)  {
				throw new CloudException("Regression-check error randomly thrown");
			}
			
		}
	}
	
	@Override
	public void create(EclFlags v) {
		try {
			
				PreparedStatement s = con.prepareStatement("INSERT INTO FLAG VALUES(?,?,?,?,?,?,?)");
				s.setLong(1, v.getTxNo());
				s.setInt(2, v.getReconSeq());
				s.setLong(3, v.getDeleteTx());
				s.setLong(4,  v.getOwnerTs());
				s.setInt(5,  v.getState());
				s.setInt(6, v.getSiteId());
				s.setString(7, null);
				forceErrorRandomly();
				s.execute();
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}

	@Override
	public void update(EclFlags v) {
		try {
			
				PreparedStatement s = con.prepareStatement("UPDATE FLAG SET reconSeq=?,deleteTx=?,ownerTs=?,state=? WHERE siteId=? AND txNo=? ");
				s.setInt(1, v.getReconSeq());
				s.setLong(2, v.getDeleteTx());
				s.setLong(3, v.getOwnerTs());
				s.setInt(4, v.getState());
				s.setInt(5, v.getSiteId());
				s.setLong(6, v.getTxNo());
				forceErrorRandomly();
				s.execute();
			
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public void delete(EclFlags v) {
		try {
			PreparedStatement s = con.prepareStatement("DELETE FROM flag WHERE siteId=? AND txNo=?");
			s.setInt(1, v.getSiteId());
			s.setLong(2, v.getTxNo());
			forceErrorRandomly();
			s.execute();
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public EclFlags toObject(ResultSet...multi) {
		ResultSet r = multi[0];
		try {
			EclFlags flags = new EclFlags();
			flags.setTxNo(r.getLong(1));
			flags.setReconSeq(r.getInt(2));
			flags.setDeleteTx(r.getLong(3));
			flags.setOwnerTs(r.getLong(4));
			flags.setState(r.getInt(5));
			flags.setSiteId(r.getInt(6));
			return flags;
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}

	@Override
	public void tableCreateIfNeeded() {
		String create = "CREATE TABLE IF NOT EXISTS flag (txNo INTEGER, "
				+ "reconSeq INTEGER, deleteTx INTEGER, ownerTs INTEGER, state INTEGER, siteId INTEGER)";
	String create2 = "CREATE VIEW IF NOT EXISTS flag_unreconned_vw AS SELECT * FROM flag WHERE reconSeq = 0 AND ((state=2 AND deleteTx != 0) OR (state=3 AND deleteTx = 0))";
	String index1 = "CREATE INDEX IF NOT EXISTS f_delete_index ON FLAG (deleteTx)";
	String index2 = "CREATE INDEX IF NOT EXISTS f_recon_index ON FLAG (reconSeq)";
	String index3 = "CREATE INDEX IF NOT EXISTS f_state_index ON FLAG (state)";
	String index4 = "CREATE INDEX IF NOT EXISTS f_state_index ON FLAG (siteId)";
	String index5 = "CREATE UNIQUE INDEX IF NOT EXISTS f_enforce ON FLAG (siteId,txNo)";

	String alter = " ALTER TABLE flag ADD COLUMN deletedBy INTEGER;";
	String trigger = "CREATE TRIGGER IF NOT EXISTS flag_delete_trigger AFTER INSERT ON flag BEGIN update flag set deletedBy = (SELECT f2.txNo FROM flag f2 WHERE flag.txNo=f2.deleteTx) where new.siteId = siteId; END;";
	
	Statement state;
	try {
		state = con.createStatement();
		state.execute(create);
		state.execute(create2);
		state.execute(index1);
		state.execute(index2);
		state.execute(index3);
		state.execute(index4);
		state.execute(index5);
		try {
			state.execute(alter);
		} catch(SQLException e)  {
			// Supress the error if we've already altered it before
			if(e.getMessage().contains("duplicate") == false) {
				throw e;
			}
		}
		state.execute(trigger);
	} catch (SQLException e) {
		throw new CloudException(e);
	}
		
	}


	
}
