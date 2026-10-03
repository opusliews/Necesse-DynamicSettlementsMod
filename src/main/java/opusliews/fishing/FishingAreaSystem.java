package opusliews.fishing;

import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.jobs.AbstractLevelJob;
import necesse.level.maps.levelData.jobs.FishingTileLevelJob;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;

public final class FishingAreaSystem {
	private FishingAreaSystem() {
	}

	public static boolean isFishingJobAllowed(HumanMob human, AbstractLevelJob job) {
		if (!(job instanceof FishingTileLevelJob)) return true;
		if (human == null || !human.isSettler() || job.getLevel() == null) return true;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return true;
		FishingAreaLevelData data = FishingAreaLevelData.get(settlement.getLevel(), false);
		if (data == null) return true;
		int areaUniqueID = data.getAssignedAreaID(settlement.uniqueID, human.getUniqueID());
		if (areaUniqueID == 0) return true;
		FishingAreaLevelData.FishingArea area = data.getArea(settlement.uniqueID, areaUniqueID);
		if (area == null) {
			if (Logging.logEnabled) Logging.logMessage("[FishingAreas] Missing assigned fishing area settler=" + human.getUniqueID() + " area=" + areaUniqueID + "; rejecting fishing until assignment is repaired");
			return false;
		}
		return area.levelIdentifier.equals(job.getLevel().getIdentifier()) && area.zoning.containsTile(job.getTileX(), job.getTileY());
	}
}
