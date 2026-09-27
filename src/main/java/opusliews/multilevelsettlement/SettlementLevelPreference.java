package opusliews.multilevelsettlement;

public enum SettlementLevelPreference {
	AUTO,
	SURFACE,
	CAVE;

	public SettlementLevelPreference next() {
		SettlementLevelPreference[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static SettlementLevelPreference fromOrdinal(int ordinal) {
		SettlementLevelPreference[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AUTO;
	}
}
