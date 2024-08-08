package quadric.gfs;

public enum Gfs {
		gfsNone(-1),
		gfsAdHoc(0),
		gfsDaily(1),
		gfsWeekly(2),
		gfsMonthlyDaily(3),
		gfsMonthly(4),
		gfsYearly(5),
		gfsEternal(6),
		gfsCandidate(7);
	
	public final int val;
	
	private Gfs(int val) {
		this.val = val;
	}
	
	public static Gfs offspring(Gfs parent) {
		
		switch(parent) {
			case gfsWeekly: case gfsMonthlyDaily: return gfsDaily;
			case gfsMonthly: return gfsWeekly;
			case gfsYearly: return gfsMonthly;
			default: return gfsNone;
		}
	}
	
	public Gfs child() {
		return offspring(this);
	}
	
	public static Gfs fromInt(int i) {
		for(Gfs g: Gfs.values()) {
			if(g.val == i) {
				return g;
			}
		}
		throw new IllegalStateException("Gfs unknown");
	}

}
