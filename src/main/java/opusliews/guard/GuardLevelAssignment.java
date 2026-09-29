package opusliews.guard;

import opusliews.multilevelsettlement.SettlementLevelType;

public enum GuardLevelAssignment {
	SURFACE(SettlementLevelType.SURFACE),
	CAVE(SettlementLevelType.CAVE);

	public final SettlementLevelType levelType;

	GuardLevelAssignment(SettlementLevelType levelType) {
		this.levelType = levelType;
	}

	public GuardLevelAssignment next() {
		return this == SURFACE ? CAVE : SURFACE;
	}

	public static GuardLevelAssignment fromOrdinal(int ordinal) {
		GuardLevelAssignment[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SURFACE;
	}
}
