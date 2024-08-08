package quadric.ods.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import quadric.blockvaulter.CloudException;
import quadric.ods.PrintRef;
import quadric.util.Print;

public class PrintCrud  extends Crud<PrintRef> {

	public PrintCrud() {
		super();
	}
	
	public PrintCrud(Connection con) { 
		super(con);
	}
	
	@Override
	public void tableCreateIfNeeded() {
		String create = "CREATE TABLE IF NOT EXISTS prints (print BLOB PRIMARY KEY, refCount INTEGER, hot INTEGER)";
		String create2 = "CREATE TABLE IF NOT EXISTS prints_tmp (print BLOB PRIMARY KEY)";
		String index1 = "CREATE INDEX IF NOT EXISTS f_ref_index ON prints (refCount)";
		Statement state;
		try {
			state = con.createStatement();
			state.execute(create);
			state.execute(index1);
			state.execute(create2);
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public void create(PrintRef v) {
		try {
			PreparedStatement s = con.prepareStatement("INSERT INTO prints VALUES(?,?,0)");
			s.setObject(1, v.store());
			s.setLong(2, v.getRefCount());
			s.execute();
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}

	@Override
	public void update(PrintRef v) {
		try {
			PreparedStatement s = con.prepareStatement("UPDATE prints SET refCount=? WHERE print=?");
			s.setLong(1, v.getRefCount());
			s.setObject(2, v.store());
			s.execute();
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public void delete(PrintRef v) {
		try {
			PreparedStatement s = con.prepareStatement("DELETE FROM prints WHERE print=? AND refCount=0");
			s.setObject(1, v.store());
			s.execute();
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

	@Override
	public PrintRef toObject(ResultSet...multi) {
		ResultSet r = multi[0];
		try {
			PrintRef ref = new PrintRef();
			ref.load(r.getBytes(1));
			ref.setRefCount(r.getLong(2));
			return ref;
		} catch(SQLException sqle) {
			throw new CloudException(sqle);
		}
		 
		
	}

	public Connection getConnection() {
		return con;
	}

}
