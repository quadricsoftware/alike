package quadric.ods.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import quadric.blockvaulter.CloudException;
import quadric.util.Pair;

public class SettingCrud extends Crud<Pair<String,String>> {

	@Override
	public void tableCreateIfNeeded() {
		String create = "CREATE TABLE IF NOT EXISTS settings (name VARCHAR PRIMARY KEY, val VARCHAR)";
		Statement state;
		try {
			state = con.createStatement();
			state.execute(create);
		} catch (SQLException e) {
			throw new CloudException(e);
		}
	}
	
	@Override
	public void create(Pair<String, String> v) {
		update(v);
		
	}
	
	@Override
	public void update(Pair<String, String> v) {
		try {
			PreparedStatement st = con.prepareStatement("INSERT OR REPLACE INTO settings VALUES (?, ?)");
			st.setString(1, v.first);
			st.setString(2, v.second);
			st.execute();
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}
	
	@Override
	public void delete(Pair<String, String> v) {
		try {
			PreparedStatement st = con.prepareStatement("DELETE FROM settings WHERE name=?");
			st.setString(1, v.first);
			st.execute();
		} catch (SQLException e) {
			throw new CloudException(e);
		}
		
	}
	
	@Override
	public Pair<String, String> toObject(ResultSet... r) {
		try {
			return new Pair<String,String>(r[0].getString(1), r[0].getString(2));
		} catch(SQLException e) {
			throw new CloudException(e);
		}
	}
}
