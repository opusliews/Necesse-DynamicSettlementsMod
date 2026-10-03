package opusliews.multilevelsettlement;

import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.ZoneTester;
import opusliews.fishing.FishingAreaLevelData;
import opusliews.zones.SettlementIndependentZoneSystem;

public final class SettlementLevelZoneSystem {
	private SettlementLevelZoneSystem() {
	}

	public static ZoneTester getCurrentLevelJobRestriction(HumanMob human) {
		if (human == null || human.getLevel() == null || !human.isSettler()) return null;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return null;
		LevelSettler levelSettler = settlement.getSettler(human.getUniqueID());
		if (levelSettler == null) return null;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null || !domain.containsLevel(human.getLevel().getIdentifier())) return null;
		LevelIdentifier currentLevel = human.getLevel().getIdentifier();
		return (tileX, tileY) -> isTileAllowed(levelSettler, domain, currentLevel, tileX, tileY);
	}

	public static SettlementRouteRestriction getRouteRestriction(HumanMob human) {
		if (human == null || !human.isSettler()) return SettlementRouteRestriction.ALLOW_ALL;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return SettlementRouteRestriction.ALLOW_ALL;
		LevelSettler levelSettler = settlement.getSettler(human.getUniqueID());
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (levelSettler == null || domain == null) return SettlementRouteRestriction.ALLOW_ALL;
		return (levelIdentifier, tileX, tileY) -> isTileAllowed(levelSettler, domain, levelIdentifier, tileX, tileY);
	}

	public static boolean isTileAllowed(LevelSettler settler, SettlementLevelDomain domain, LevelIdentifier levelIdentifier, int tileX, int tileY) {
		if (settler == null || domain == null || levelIdentifier == null) return false;
		if (!domain.isTileWithinBounds(levelIdentifier, tileX, tileY)) return false;
		int restrictUniqueID = settler.getRestrictZoneUniqueID();
		if (restrictUniqueID == 0) return true;
		return isTileInRestrictZone(settler.data, levelIdentifier, restrictUniqueID, tileX, tileY);
	}

	public static boolean isTileInRestrictZone(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int restrictUniqueID, int tileX, int tileY) {
		if (settlement == null || levelIdentifier == null || restrictUniqueID == 0) return restrictUniqueID == 0;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null || !domain.containsLevel(levelIdentifier)) return false;
		return SettlementIndependentZoneSystem.isTileInRestriction(settlement, restrictUniqueID, levelIdentifier, tileX, tileY);
	}

	public static void removeSettlement(ServerSettlementData settlement) {
		if (settlement == null) return;
		SettlementIndependentZoneSystem.removeSettlement(settlement);
		if (settlement.networkData != null && settlement.networkData.level != null) {
			FishingAreaLevelData fishingData = FishingAreaLevelData.get(settlement.networkData.level, false);
			if (fishingData != null) fishingData.clearSettlement(settlement.uniqueID);
		}
	}
}
