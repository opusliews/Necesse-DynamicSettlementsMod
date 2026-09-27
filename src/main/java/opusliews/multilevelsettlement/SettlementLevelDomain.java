package opusliews.multilevelsettlement;

import necesse.engine.util.LevelIdentifier;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;

import java.awt.Rectangle;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class SettlementLevelDomain {
	private final ServerSettlementData settlement;
	private final Map<SettlementLevelType, LevelIdentifier> levelIdentifiers = new EnumMap<>(SettlementLevelType.class);

	SettlementLevelDomain(ServerSettlementData settlement, LevelIdentifier surfaceIdentifier, LevelIdentifier caveIdentifier) {
		this.settlement = Objects.requireNonNull(settlement, "settlement");
		levelIdentifiers.put(SettlementLevelType.SURFACE, Objects.requireNonNull(surfaceIdentifier, "surfaceIdentifier"));
		levelIdentifiers.put(SettlementLevelType.CAVE, Objects.requireNonNull(caveIdentifier, "caveIdentifier"));
	}

	public ServerSettlementData getSettlement() {
		return settlement;
	}

	public int getSettlementUniqueID() {
		return settlement.uniqueID;
	}

	public LevelIdentifier getLevelIdentifier(SettlementLevelType levelType) {
		return levelIdentifiers.get(levelType);
	}

	public SettlementLevelType getLevelType(LevelIdentifier identifier) {
		if (identifier == null) return null;
		for (Map.Entry<SettlementLevelType, LevelIdentifier> entry : levelIdentifiers.entrySet()) {
			if (entry.getValue().equals(identifier)) return entry.getKey();
		}
		return null;
	}

	public boolean containsLevel(LevelIdentifier identifier) {
		return getLevelType(identifier) != null;
	}

	public Rectangle getTileBounds() {
		Rectangle bounds = settlement.boundsManager.getTileRectangle();
		if (bounds == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Could not resolve settlement bounds settlement=" + settlement.uniqueID);
			return null;
		}
		return new Rectangle(bounds);
	}

	public Rectangle getTileBounds(SettlementLevelType levelType) {
		if (levelType == null || getLevelIdentifier(levelType) == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Requested bounds for invalid level type settlement=" + settlement.uniqueID + " levelType=" + levelType);
			return null;
		}
		return getTileBounds();
	}

	public boolean isTileWithinBounds(LevelIdentifier identifier, int tileX, int tileY) {
		if (!containsLevel(identifier)) return false;
		Rectangle bounds = getTileBounds();
		return bounds != null && bounds.contains(tileX, tileY);
	}

	public boolean isTileWithinBounds(SettlementLevelPosition position) {
		return position != null && isTileWithinBounds(position.levelIdentifier, position.tileX, position.tileY);
	}

	public Level getLoadedLevel(SettlementLevelType levelType) {
		LevelIdentifier identifier = getLevelIdentifier(levelType);
		if (identifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelSettlement] Cannot get loaded level for missing identifier settlement=" + settlement.uniqueID + " levelType=" + levelType);
			return null;
		}
		Level level = settlement.getServer().world.levelManager.getLevel(identifier);
		if (level == null && Logging.logEnabled) {
			Logging.logMessage("[MultiLevelSettlement] Level is not currently loaded settlement=" + settlement.uniqueID + " levelType=" + levelType + " identifier=" + identifier);
		}
		return level;
	}

	@Override
	public String toString() {
		return "SettlementLevelDomain{settlement=" + settlement.uniqueID + ", surface=" + getLevelIdentifier(SettlementLevelType.SURFACE) + ", cave=" + getLevelIdentifier(SettlementLevelType.CAVE) + "}";
	}
}
