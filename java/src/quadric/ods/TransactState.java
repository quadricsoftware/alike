package quadric.ods;

public enum TransactState {
		TransactBegin(1),
		TransactActive(2),
		TransactVaulted(3),
		TransactReconned(4),
		TransactRollback(0);
		
		private final int value;
	
		TransactState(final int v) {
			value = v;
		}
		
		public int getValue() { return value; }

}
