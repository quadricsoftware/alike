package quadric.ods.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Connection;

import quadric.blockvaulter.CloudException;

public class UuidPairCrud extends Crud<UuidPair> {

	public UuidPairCrud(Connection con) {
		super(con);
	}
	
	public UuidPairCrud() {
		super();
	}
	
	@Override
	public void tableCreateIfNeeded() {
		String s1 = "CREATE TABLE IF NOT EXISTS uuid (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid VARCHAR UNIQUE)";
		String s2 = "CREATE TRIGGER IF NOT EXISTS uuid_trigger AFTER INSERT ON vversion BEGIN INSERT OR IGNORE INTO uuid VALUES (NULL, new.uuid); END";
		String s3 = "CREATE TRIGGER IF NOT EXISTS uuid_trigger_d AFTER DELETE ON vversion BEGIN DELETE FROM uuid WHERE uuid NOT IN (SELECT v.UUID FROM vversion v); END";

		Statement state;
		try {
			state = con.createStatement();
			state.execute(s1);
			state.execute(s2);
			state.execute(s3);
		} catch (SQLException e) {
			throw new CloudException(e);
		}

	}

	@Override
	public void create(UuidPair v) {
		;

	}

	@Override
	public void update(UuidPair v) {
		;

	}

	@Override
	public void delete(UuidPair v) {
		;

	}

	@Override
	public UuidPair toObject(ResultSet... r) {
		ResultSet rs = r[0];
		
		try {
			return new UuidPair(rs.getLong(1), rs.getString(2));
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}

}
