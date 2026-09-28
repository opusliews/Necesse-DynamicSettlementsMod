package opusliews.multilevelsettlement;

import necesse.entity.mobs.job.EntityJobWorker;
import necesse.inventory.InventoryRange;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import necesse.level.maps.levelData.settlementData.SettlementRequestInventory;
import necesse.level.maps.levelData.settlementData.SettlementRequestOptions;
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
	public boolean estimateCanMoveTo(EntityJobWorker worker) {
		return worker != null && worker.estimateCanMoveTo(tileX, tileY, true);
	}

	@Override
	public SettlementRequestInventory getFuelInventory() {
		SettlementWorkstationLevelObject workstationObject = getWorkstationObject();
		if (workstationObject != null) {
			SettlementRequestOptions fuelRequestOptions = workstationObject.getFuelRequestOptions();
			if (fuelRequestOptions != null) {
				if (fuelInventory == null) {
					fuelInventory = new SettlementRequestInventory(level, tileX, tileY, fuelRequestOptions) {
						@Override
						public InventoryRange getInventoryRange() {
							SettlementWorkstationLevelObject current = SettlementLevelWorkstation.this.getWorkstationObject();
							return current == null ? null : current.getFuelInventoryRange();
						}

						@Override
						public boolean isStorageValid() {
							return SettlementLevelStorageManager.hasWorkstation(data, SettlementLevelWorkstation.this);
						}
					};
				}
				if (!fuelInventory.isTileValid()) fuelInventory = null;
				return fuelInventory;
			}
		}
		fuelInventory = null;
		return null;
	}

	@Override
	public SettlementInventory getProcessingInputInventory() {
		if (!isProcessingWorkstation()) {
			processingInputInventory = null;
			return null;
		}
		if (processingInputInventory == null) {
			processingInputInventory = new SettlementInventory(level, tileX, tileY) {
				@Override
				public InventoryRange getInventoryRange() {
					SettlementWorkstationLevelObject current = SettlementLevelWorkstation.this.getWorkstationObject();
					return current == null ? null : current.getProcessingInputRange();
				}

				@Override
				public boolean isStorageValid() {
					return SettlementLevelStorageManager.hasWorkstation(data, SettlementLevelWorkstation.this);
				}
			};
		}
		if (!processingInputInventory.isTileValid()) processingInputInventory = null;
		return processingInputInventory;
	}

	@Override
	public SettlementInventory getProcessingOutputInventory() {
		if (!isProcessingWorkstation()) {
			processingOutputInventory = null;
			return null;
		}
		if (processingOutputInventory == null) {
			processingOutputInventory = new SettlementInventory(level, tileX, tileY) {
				@Override
				public InventoryRange getInventoryRange() {
					SettlementWorkstationLevelObject current = SettlementLevelWorkstation.this.getWorkstationObject();
					return current == null ? null : current.getProcessingOutputRange();
				}

				@Override
				public boolean isStorageValid() {
					return SettlementLevelStorageManager.hasWorkstation(data, SettlementLevelWorkstation.this);
				}
			};
		}
		processingOutputInventory.filter = new ItemCategoriesFilter(0, 0, false);
		if (!processingOutputInventory.isTileValid()) processingOutputInventory = null;
		return processingOutputInventory;
	}

	@Override
	public SettlementWorkstationLevelObject getWorkstationObject() {
		GameObject gameObject = level.getObject(tileX, tileY);
		return gameObject instanceof SettlementWorkstationObject ? new SettlementWorkstationLevelObject(level, tileX, tileY) : null;
	}
}
