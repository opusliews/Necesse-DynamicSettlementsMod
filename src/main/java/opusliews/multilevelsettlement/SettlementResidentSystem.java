package opusliews.multilevelsettlement;

import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.Mob;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import necesse.level.maps.regionSystem.Region;
import opusliews.logging.Logging;

public final class SettlementResidentSystem {
	private SettlementResidentSystem() {
	}

	public static boolean isSettlerInSettlementDomain(SettlerMob settler) {
		if (settler == null || !settler.isSettler()) return false;
		Mob mob = settler.getMob();
		if (mob == null || mob.getLevel() == null || !mob.getLevel().isServer()) return false;

		ServerSettlementData settlement = settler.getSettlerSettlementServerData();
		if (settlement == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelResident] Could not resolve settlement data for settler=" + describeMob(mob));
			return false;
		}
		return isMobInsideDomain(mob, SettlementMultiLevelSystem.get(settlement));
	}

	public static boolean isSettlerInSettlementDomain(SettlerMob settler, NetworkSettlementData settlementNetworkData) {
		if (settler == null || settlementNetworkData == null || !settler.isSettler()) return false;
		if (settler.getSettlementUniqueID() != settlementNetworkData.uniqueID) return false;
		Mob mob = settler.getMob();
		if (mob == null || mob.getLevel() == null || !mob.getLevel().isServer()) return false;

		ServerSettlementData settlement = settler.getSettlerSettlementServerData();
		if (settlement == null || settlement.uniqueID != settlementNetworkData.uniqueID) return false;
		return isMobInsideDomain(mob, SettlementMultiLevelSystem.get(settlement));
	}

	public static boolean isSettlerInLoadedSettlementDomain(SettlerMob settler) {
		if (!isSettlerInSettlementDomain(settler)) return false;
		Mob mob = settler.getMob();
		Level level = mob.getLevel();
		int regionX = level.regionManager.getRegionXByTileLimited(mob.getTileX());
		int regionY = level.regionManager.getRegionYByTileLimited(mob.getTileY());
		Region region = level.regionManager.getRegion(regionX, regionY, false);
		return region != null;
	}

	public static boolean isSettlerInLoadedSettlementDomain(SettlerMob settler, NetworkSettlementData settlementNetworkData) {
		if (!isSettlerInSettlementDomain(settler, settlementNetworkData)) return false;
		Mob mob = settler.getMob();
		Level level = mob.getLevel();
		int regionX = level.regionManager.getRegionXByTileLimited(mob.getTileX());
		int regionY = level.regionManager.getRegionYByTileLimited(mob.getTileY());
		Region region = level.regionManager.getRegion(regionX, regionY, false);
		return region != null;
	}

	public static SettlerMob findLiveDomainMob(LevelSettler levelSettler) {
		if (levelSettler == null || levelSettler.data == null) return null;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(levelSettler.data);
		if (domain == null) return null;

		for (SettlementLevelType levelType : SettlementLevelType.values()) {
			Level level = domain.getLoadedLevel(levelType);
			if (level == null) continue;
			Mob mob = level.entityManager.mobs.get(levelSettler.mobUniqueID, false);
			if (mob instanceof SettlerMob) return (SettlerMob)mob;
		}
		return null;
	}

	public static boolean isCaveSettlerIsolated(SettlerMob settler) {
		if (settler == null || !settler.isSettler()) return false;
		Mob mob = settler.getMob();
		if (mob == null || mob.getLevel() == null || !mob.getLevel().isServer()) return false;
		ServerSettlementData settlement = settler.getSettlerSettlementServerData();
		if (settlement == null) return false;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null || domain.getLevelType(mob.getLevel().getIdentifier()) != SettlementLevelType.CAVE) return false;
		return SettlementLadderSystem.getValidLinks(domain, false).isEmpty();
	}

	public static void onLadderAvailabilityChanged(SettlementLevelDomain domain) {
		if (domain == null) return;
		SettlementCaveBedSystem.invalidateStrandedCache(domain.getSettlement());
		boolean isolated = SettlementLadderSystem.getValidLinks(domain, false).isEmpty();
		if (!Logging.logEnabled) return;

		Level cave = domain.getLoadedLevel(SettlementLevelType.CAVE);
		int caveResidents = 0;
		if (cave != null) {
			for (Mob mob : cave.entityManager.mobs) {
				if (!(mob instanceof SettlerMob)) continue;
				SettlerMob settler = (SettlerMob)mob;
				if (settler.getSettlementUniqueID() != domain.getSettlementUniqueID()) continue;
				if (!domain.isTileWithinBounds(cave.getIdentifier(), mob.getTileX(), mob.getTileY())) continue;
				caveResidents++;
			}
		}
		Logging.logMessage("[MultiLevelResident] Ladder availability changed settlement=" + domain.getSettlementUniqueID() + " isolated=" + isolated + " caveResidents=" + caveResidents);
	}

	private static boolean isMobInsideDomain(Mob mob, SettlementLevelDomain domain) {
		if (mob == null || mob.getLevel() == null || domain == null) return false;
		LevelIdentifier identifier = mob.getLevel().getIdentifier();
		return domain.containsLevel(identifier) && domain.isTileWithinBounds(identifier, mob.getTileX(), mob.getTileY());
	}

	private static String describeMob(Mob mob) {
		if (mob == null) return "null";
		return mob.getStringID() + "#" + mob.getUniqueID() + " level=" + (mob.getLevel() == null ? "null" : mob.getLevel().getIdentifier()) + " tile=" + mob.getTileX() + "," + mob.getTileY();
	}
}
