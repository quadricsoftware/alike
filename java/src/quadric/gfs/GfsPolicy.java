package quadric.gfs;

public class GfsPolicy {
	/* 
	 * Cardinality counts from zero, not from one
	 * For weeklies, Su = 0, M = 1, Tu = 2, W = 3, Th = 4, F = 5, Sa = 6 
	 */
	public int card;
	public int versions;
	public Gfs gfs;
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + card;
		result = prime * result + ((gfs == null) ? 0 : gfs.hashCode());
		result = prime * result + versions;
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GfsPolicy other = (GfsPolicy) obj;
		if (card != other.card)
			return false;
		if (gfs != other.gfs)
			return false;
		if (versions != other.versions)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "" + gfs + " card: " + card + " versions: " + versions;
	}
	
	
	
	
}
