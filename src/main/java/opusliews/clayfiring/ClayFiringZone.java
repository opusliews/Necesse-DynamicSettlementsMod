package opusliews.clayfiring;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.GlobalIngredientRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.EventVariable;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.SurfaceGrassObject;
import necesse.level.maps.Level;
import necesse.level.maps.hudManager.HudDrawElement;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageGlobalIngredientIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageItemIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecordsRegionData;
import necesse.level.maps.levelData.settlementData.zones.SettlementTileTickZone;
import opusliews.charcoal.CharcoalProductionZone;
import opusliews.jobs.ClayFiringCleanupLevelJob;
import opusliews.jobs.ClayFiringProductionLevelJob;
import opusliews.logging.Logging;
import opusliews.tile.CharcoalPitSystem;
import opusliews.tile.FiringGroundSystem;
import opusliews.tile.ShallowHoleTile;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public class ClayFiringZone extends SettlementTileTickZone {
	public static final String stringID = "clayfiring";
	public static final EventVariable hideZones = new EventVariable(false);
	private static final Map<Integer, Long> lastDiagnosticLog = new ConcurrentHashMap<>();
	private static final long diagnosticInterval = 5000L;

	@Override
	protected void handleTile(Point tile) {
		Level level = manager.data.getLevel();
		boolean canProduce = canProduce();
		boolean hasMaterials = canProduce && hasEnoughMaterials();
		boolean validCandidate = hasMaterials && isValidCandidate(level, tile.x, tile.y, null);

		logDiagnosticIfDue(level, canProduce, hasMaterials, tile, validCandidate);
		if (!canProduce || !hasMaterials || !validCandidate) return;

		if (hasAnyProductionJob()) return;

		level.jobsLayer.addJob(new ClayFiringProductionLevelJob(tile.x, tile.y, this));
		Logging.logMessage("[ClayFiring] Added production job zone=" + getUniqueID() + " tile=" + tile.x + "," + tile.y);
	}

	private boolean hasAnyProductionJob() {
		if (manager == null) return false;
		Level level = manager.data.getLevel();
		ClayFiringAutomationLevelData automation = ClayFiringAutomationLevelData.get(level, false);
		if (automation != null && automation.hasActiveProduction()) return true;
		for (Object value : manager.getZones().values()) {
			if (!(value instanceof ClayFiringZone)) continue;
			ClayFiringZone zone = (ClayFiringZone)value;
			for (Point point : zone.zoning.getTiles()) {
				boolean exists = level.jobsLayer.streamJobsInTile(point.x, point.y)
						.anyMatch(job -> job instanceof ClayFiringProductionLevelJob);
				if (exists) return true;
			}
		}
		return false;
	}

	private void logDiagnosticIfDue(Level level, boolean canProduce, boolean hasMaterials, Point tile, boolean validCandidate) {
		if (!Logging.logEnabled) return;
		long now = System.currentTimeMillis();
		long last = lastDiagnosticLog.getOrDefault(getUniqueID(), 0L);
		if (now - last < diagnosticInterval) return;
		lastDiagnosticLog.put(getUniqueID(), now);

		ClayFiringSettingsLevelData settings = ClayFiringSettingsLevelData.get(level, false);
		boolean repeat = settings != null && settings.isRepeatForever();
		List<ClayFiringCatalog.Entry> needed = getNeededEntries();
		int anyLogID = GlobalIngredientRegistry.getGlobalIngredientID("anylog");
		SettlementStorageGlobalIngredientIDIndex logIndex = manager.data.storageRecords.getIndex(SettlementStorageGlobalIngredientIDIndex.class);
		SettlementStorageRecordsRegionData logs = logIndex.getGlobalIngredient(anyLogID);
		int logCount = logs == null ? 0 : logs.getTotalItems();
		SettlementStorageItemIDIndex itemIndex = manager.data.storageRecords.getIndex(SettlementStorageItemIDIndex.class);

		StringBuilder neededSummary = new StringBuilder();
		for (ClayFiringCatalog.Entry entry : needed) {
			if (neededSummary.length() > 0) neededSummary.append(';');
			int unfired = itemIndex.getTotalItems(entry.unfiredItemStringID);
			int fired = itemIndex.getTotalItems(entry.firedItemStringID);
			int target = settings == null ? 0 : settings.getProduceUntilUnitsStocked(entry.firedItemStringID);
			neededSummary.append(entry.firedItemStringID)
					.append(" target=").append(target)
					.append(" fired=").append(fired)
					.append(" unfired=").append(unfired);
		}

		Logging.logMessage("[ClayFiring] Zone diagnostic zone=" + getUniqueID()
				+ " settings=" + (settings != null)
				+ " repeat=" + repeat
				+ " catalog=" + ClayFiringCatalog.getEntries().size()
				+ " needed=" + needed.size()
				+ " canProduce=" + canProduce
				+ " logs=" + logCount + "/" + ClayFiringProductionLevelJob.requiredLogs
				+ " hasMaterials=" + hasMaterials
				+ " sampledTile=" + tile.x + "," + tile.y
				+ " sampledCandidate=" + validCandidate
				+ " items=[" + neededSummary + "]");
	}

	public boolean canProduce() {
		if (manager == null || manager.data == null) return false;
		ClayFiringSettingsLevelData settings = ClayFiringSettingsLevelData.get(manager.data.getLevel(), false);
		if (settings == null) return false;
		if (settings.isRepeatForever()) return !ClayFiringCatalog.getEntries().isEmpty();

		SettlementStorageItemIDIndex index = manager.data.storageRecords.getIndex(SettlementStorageItemIDIndex.class);
		for (ClayFiringCatalog.Entry entry : ClayFiringCatalog.getEntries()) {
			int target = settings.getProduceUntilUnitsStocked(entry.firedItemStringID);
			if (target <= 0) continue;
			if (getEffectiveFiredAmount(index, entry.firedItemStringID) < target) return true;
		}
		return false;
	}

	public boolean needsProductionAfter(List<InventoryItem> additionalFiredItems) {
		if (manager == null || manager.data == null) return false;
		ClayFiringSettingsLevelData settings = ClayFiringSettingsLevelData.get(manager.data.getLevel(), false);
		if (settings == null) return false;
		if (settings.isRepeatForever()) return !ClayFiringCatalog.getEntries().isEmpty();
		SettlementStorageItemIDIndex index = manager.data.storageRecords.getIndex(SettlementStorageItemIDIndex.class);
		for (ClayFiringCatalog.Entry entry : ClayFiringCatalog.getEntries()) {
			int target = settings.getProduceUntilUnitsStocked(entry.firedItemStringID);
			if (target <= 0) continue;
			int additional = 0;
			if (additionalFiredItems != null) {
				for (InventoryItem item : additionalFiredItems) {
					if (item != null && item.item.getStringID().equals(entry.firedItemStringID)) additional += item.getAmount();
				}
			}
			if (index.getTotalItems(entry.firedItemStringID) + additional < target) return true;
		}
		return false;
	}

	public List<ClayFiringCatalog.Entry> getNeededEntries() {
		ArrayList<ClayFiringCatalog.Entry> result = new ArrayList<>();
		if (manager == null || manager.data == null) return result;
		ClayFiringSettingsLevelData settings = ClayFiringSettingsLevelData.get(manager.data.getLevel(), false);
		if (settings == null) return result;
		SettlementStorageItemIDIndex index = manager.data.storageRecords.getIndex(SettlementStorageItemIDIndex.class);

		for (ClayFiringCatalog.Entry entry : ClayFiringCatalog.getEntries()) {
			if (settings.isRepeatForever()) {
				result.add(entry);
				continue;
			}
			int target = settings.getProduceUntilUnitsStocked(entry.firedItemStringID);
			if (target > 0 && getEffectiveFiredAmount(index, entry.firedItemStringID) < target) result.add(entry);
		}
		return result;
	}

	public Map<String, Integer> getNeededUnfiredAmounts(int maxTotal) {
		LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
		if (manager == null || manager.data == null || maxTotal <= 0) return result;

		ClayFiringSettingsLevelData settings = ClayFiringSettingsLevelData.get(manager.data.getLevel(), false);
		if (settings == null) return result;
		SettlementStorageItemIDIndex index = manager.data.storageRecords.getIndex(SettlementStorageItemIDIndex.class);

		for (ClayFiringCatalog.Entry entry : ClayFiringCatalog.getEntries()) {
			int amount;
			if (settings.isRepeatForever()) {
				amount = maxTotal;
			} else {
				int target = settings.getProduceUntilUnitsStocked(entry.firedItemStringID);
				if (target <= 0) continue;
				amount = Math.min(maxTotal, Math.max(0, target - getEffectiveFiredAmount(index, entry.firedItemStringID)));
			}
			if (amount > 0) result.put(entry.unfiredItemStringID, amount);
		}
		return result;
	}

	private int getEffectiveFiredAmount(SettlementStorageItemIDIndex index, String firedItemStringID) {
		int stored = index.getTotalItems(firedItemStringID);
		ClayFiringAutomationLevelData automation = ClayFiringAutomationLevelData.get(manager.data.getLevel(), false);
		return stored + (automation == null ? 0 : automation.getPotentialFiredAmount(firedItemStringID));
	}

	public boolean hasEnoughMaterials() {
		if (manager == null || manager.data == null) return false;
		int anyLogID = GlobalIngredientRegistry.getGlobalIngredientID("anylog");
		SettlementStorageGlobalIngredientIDIndex logIndex = manager.data.storageRecords.getIndex(SettlementStorageGlobalIngredientIDIndex.class);
		SettlementStorageRecordsRegionData logs = logIndex.getGlobalIngredient(anyLogID);
		if (logs == null || logs.getTotalItems() < ClayFiringProductionLevelJob.requiredLogs) return false;

		SettlementStorageItemIDIndex itemIndex = manager.data.storageRecords.getIndex(SettlementStorageItemIDIndex.class);
		for (ClayFiringCatalog.Entry entry : getNeededEntries()) {
			if (itemIndex.getTotalItems(entry.unfiredItemStringID) > 0) return true;
		}
		return false;
	}

	public static boolean isValidCandidate(Level level, int tileX, int tileY, ClayFiringProductionLevelJob currentJob) {
		if (level == null || !level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) return false;
		if (!FiringGroundSystem.isSupportedGround(level, tileX, tileY)) return false;
		GameObject object = level.getObject(tileX, tileY);
		if (object.getID() != 0 && !(object instanceof SurfaceGrassObject)) return false;
		if (CharcoalPitSystem.isPitTile(level, tileX, tileY)) return false;
		return hasClearNeighborhood(level, tileX, tileY, currentJob);
	}

	public static boolean isValidReusableHole(Level level, int tileX, int tileY, ClayFiringProductionLevelJob currentJob) {
		if (level == null || !level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) return false;
		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(ShallowHoleTile.stringID) || level.getObjectID(tileX, tileY) != 0) return false;
		return hasClearNeighborhood(level, tileX, tileY, currentJob);
	}

	private static boolean hasClearNeighborhood(Level level, int tileX, int tileY, ClayFiringProductionLevelJob currentJob) {
		for (int[] offset : CharcoalProductionZone.cardinalOffsets) {
			int checkX = tileX + offset[0];
			int checkY = tileY + offset[1];
			if (!level.isTileWithinBounds(checkX, checkY)) continue;
			if (CharcoalPitSystem.isPitTile(level, checkX, checkY)) return false;
			boolean reserved = level.jobsLayer.streamJobsInTile(checkX, checkY).anyMatch(job ->
					(job instanceof ClayFiringProductionLevelJob && job != currentJob)
							|| job instanceof ClayFiringCleanupLevelJob);
			if (reserved) return false;
		}
		return true;
	}

	@Override
	public boolean isHiddenSetting() {
		return (Boolean)hideZones.get();
	}

	@Override
	protected GameMessage getDefaultName(int number) {
		return new LocalMessage("ui", "clayfiringzonedefname", "number", number);
	}

	@Override
	public GameMessage getAbstractName() {
		return new LocalMessage("ui", "clayfiringzone");
	}

	@Override
	public HudDrawElement getHudDrawElement(int drawPriority, BooleanSupplier overrideShow) {
		return getHudDrawElement(drawPriority, overrideShow, new Color(181, 112, 55, 150), new Color(111, 64, 30, 75));
	}
}
