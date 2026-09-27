package opusliews.multilevelsettlement;

import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementWorkstation;
import necesse.level.maps.levelData.settlementData.SettlementWorkstationLevelObject;
import necesse.level.maps.levelData.settlementData.SettlementWorkstationObject;
import necesse.level.maps.regionSystem.Region;
import necesse.level.maps.regionSystem.SubRegion;

import java.awt.Point;

public class SettlementLevelWorkstation extends SettlementWorkstation {
	private final Level level;

	public SettlementLevelWorkstation(ServerSettlementData settlement, Level level, int tileX, int tileY) {
		super(settlement, tileX, tileY);
		this.level = level;
	}

	public Level getLevel() {
		return level;
	}

	@Override
	public void updateAdjacentSolidState() {
		isAllAdjacentSolid = true;
		for (Point tile : level.getObject(tileX, tileY).getMultiTile(level, 0, tileX, tileY).getAdjacentTiles(tileX, tileY, true)) {
			Region region = level.regionManager.getRegionByTile(tile.x, tile.y, false);
			if (region == null) continue;
			SubRegion subRegion = region.subRegionData.getSubRegionByRegion(tile.x - region.tileXOffset, tile.y - region.tileYOffset);
			if (subRegion != null && !subRegion.getType().isAlwaysSolid) {
				isAllAdjacentSolid = false;
				break;
			}
		}
	}

	@Override
	public boolean isStorageValid() {
		return SettlementLevelStorageManager.hasWorkstation(data, this);
	}

	@Override
	public SettlementWorkstationLevelObject getWorkstationObject() {
		GameObject gameObject = level.getObject(tileX, tileY);
		return gameObject instanceof SettlementWorkstationObject ? new SettlementWorkstationLevelObject(level, tileX, tileY) : null;
	}
}
