package opusliews.multilevelsettlement;

public final class SettlementLadderLink {
	public final int settlementUniqueID;
	public final int surfaceTileX;
	public final int surfaceTileY;
	public final int caveTileX;
	public final int caveTileY;

	public SettlementLadderLink(int settlementUniqueID, int surfaceTileX, int surfaceTileY, int caveTileX, int caveTileY) {
		this.settlementUniqueID = settlementUniqueID;
		this.surfaceTileX = surfaceTileX;
		this.surfaceTileY = surfaceTileY;
		this.caveTileX = caveTileX;
		this.caveTileY = caveTileY;
	}

	public int getTileX(SettlementLevelType levelType) {
		return levelType == SettlementLevelType.CAVE ? caveTileX : surfaceTileX;
	}

	public int getTileY(SettlementLevelType levelType) {
		return levelType == SettlementLevelType.CAVE ? caveTileY : surfaceTileY;
	}

	public boolean matches(SettlementLevelType levelType, int tileX, int tileY) {
		return getTileX(levelType) == tileX && getTileY(levelType) == tileY;
	}

	@Override
	public String toString() {
		return "SettlementLadderLink{settlement=" + settlementUniqueID + ", surface=" + surfaceTileX + "," + surfaceTileY + ", cave=" + caveTileX + "," + caveTileY + "}";
	}
}
