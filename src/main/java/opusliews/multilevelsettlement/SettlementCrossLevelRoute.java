package opusliews.multilevelsettlement;

public final class SettlementCrossLevelRoute {
	public final SettlementLevelPosition source;
	public final SettlementLevelPosition target;
	public final SettlementLadderLink ladder;
	public final int sourceToLadderDistance;
	public final int ladderToTargetDistance;
	public final int totalDistance;

	private SettlementCrossLevelRoute(SettlementLevelPosition source, SettlementLevelPosition target, SettlementLadderLink ladder, int sourceToLadderDistance, int ladderToTargetDistance) {
		this.source = source;
		this.target = target;
		this.ladder = ladder;
		this.sourceToLadderDistance = sourceToLadderDistance;
		this.ladderToTargetDistance = ladderToTargetDistance;
		this.totalDistance = sourceToLadderDistance + ladderToTargetDistance;
	}

	public static SettlementCrossLevelRoute sameLevel(SettlementLevelPosition source, SettlementLevelPosition target, int distance) {
		return new SettlementCrossLevelRoute(source, target, null, distance, 0);
	}

	public static SettlementCrossLevelRoute crossLevel(SettlementLevelPosition source, SettlementLevelPosition target, SettlementLadderLink ladder, int sourceToLadderDistance, int ladderToTargetDistance) {
		return new SettlementCrossLevelRoute(source, target, ladder, sourceToLadderDistance, ladderToTargetDistance);
	}

	public boolean crossesLevel() {
		return ladder != null;
	}

	@Override
	public String toString() {
		return "SettlementCrossLevelRoute{source=" + source + ", target=" + target + ", ladder=" + ladder + ", sourceToLadder=" + sourceToLadderDistance + ", ladderToTarget=" + ladderToTargetDistance + ", total=" + totalDistance + "}";
	}
}
