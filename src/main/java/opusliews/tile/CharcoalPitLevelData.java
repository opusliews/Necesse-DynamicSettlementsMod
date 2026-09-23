package opusliews.tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.pickup.ItemPickupEntity;
import necesse.entity.manager.RegionLoadedListenerEntityComponent;
import necesse.inventory.InventoryItem;
import opusliews.item.FireableMatItem;
import opusliews.clayfiring.ClayFiringAutomationLevelData;
import opusliews.jobs.CharcoalCleanupLevelJob;
import opusliews.logging.Logging;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.RegionLevelDataComponent;
import necesse.level.maps.regionSystem.Region;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import opusliews.charcoal.CharcoalProductionZone;
import opusliews.jobs.CharcoalProductionLevelJob;

public class CharcoalPitLevelData extends LevelData implements RegionLevelDataComponent, RegionLoadedListenerEntityComponent {
	public static final String managerKey = "opuscharcoalpitdata";

	private final Map<Long, List<StoredLog>> pitLogs = new HashMap<>();
	private final Map<Long, List<StoredFiringItem>> pitFiringItems = new HashMap<>();
	private final Map<Long, Long> burnEndWorldTimes = new HashMap<>();
	private final Map<Long, Long> firingBurnEndWorldTimes = new HashMap<>();
	private final Map<Long, ProductionRecoveryState> productionRecoveryStates = new HashMap<>();
	private final Map<Long, Integer> pendingCleanupPickupIDs = new HashMap<>();
	private int produceUntilUnitsStocked;
	private boolean repeatForever;
	private String lastProductionBlockedReason;
	private long lastProductionBlockedMessageTime;

	public static CharcoalPitLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) {
			return null;
		}

		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof CharcoalPitLevelData) {
			return (CharcoalPitLevelData)existing;
		}

		if (!createNewIfNull) {
			return null;
		}

		CharcoalPitLevelData data = new CharcoalPitLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}


	public enum ProductionStage {
		DUG,
		LOADED,
		COVERED
	}

	public void setProductionRecoveryState(int tileX, int tileY, ProductionStage stage, int zoneUniqueID) {
		if (stage == null) {
			productionRecoveryStates.remove(getKey(tileX, tileY));
			return;
		}
		productionRecoveryStates.put(getKey(tileX, tileY), new ProductionRecoveryState(stage, zoneUniqueID));
	}

	public void clearProductionRecoveryState(int tileX, int tileY) {
		productionRecoveryStates.remove(getKey(tileX, tileY));
	}

	public ProductionRecoveryState getProductionRecoveryState(int tileX, int tileY) {
		return productionRecoveryStates.get(getKey(tileX, tileY));
	}

	public void setPendingCleanup(int tileX, int tileY, ItemPickupEntity pickup) {
		pendingCleanupPickupIDs.put(getKey(tileX, tileY), pickup == null ? 0 : pickup.getUniqueID());
	}

	public void clearPendingCleanup(int tileX, int tileY) {
		pendingCleanupPickupIDs.remove(getKey(tileX, tileY));
	}

	public int getProduceUntilUnitsStocked() {
		return produceUntilUnitsStocked;
	}

	public boolean isRepeatForever() {
		return repeatForever;
	}

	public int getPotentialCharcoalAmount() {
		int amount = 0;
		for (Long key : burnEndWorldTimes.keySet()) {
			List<StoredLog> logs = pitLogs.get(key);
			if (logs == null) {
				continue;
			}

			for (StoredLog log : logs) {
				amount += Math.max(0, log.amount);
			}
		}
		return amount;
	}


	public boolean shouldSendProductionBlockedReason(String reason, long currentTime) {
		if (reason == null || reason.isEmpty()) {
			return false;
		}

		boolean changed = !reason.equals(lastProductionBlockedReason);
		boolean cooldownExpired = currentTime - lastProductionBlockedMessageTime >= 30000L;
		if (!changed && !cooldownExpired) {
			return false;
		}

		lastProductionBlockedReason = reason;
		lastProductionBlockedMessageTime = currentTime;
		return true;
	}

	public void clearProductionBlockedReason() {
		lastProductionBlockedReason = null;
		lastProductionBlockedMessageTime = 0L;
	}

	public void setProductionSettings(int produceUntilUnitsStocked, boolean repeatForever) {
		this.produceUntilUnitsStocked = Math.max(0, produceUntilUnitsStocked);
		this.repeatForever = repeatForever;
	}

	public void setLogs(int tileX, int tileY, List<StoredLog> logs) {
		pitLogs.put(getKey(tileX, tileY), copyLogs(logs));
	}

	public List<StoredLog> removeLogs(int tileX, int tileY) {
		List<StoredLog> logs = pitLogs.remove(getKey(tileX, tileY));
		return logs == null ? new ArrayList<>() : logs;
	}

	public List<StoredLog> getLogs(int tileX, int tileY) {
		List<StoredLog> logs = pitLogs.get(getKey(tileX, tileY));
		return logs == null ? new ArrayList<>() : copyLogs(logs);
	}

	public void setFiringItems(int tileX, int tileY, List<StoredFiringItem> items) {
		if (items == null || items.isEmpty()) {
			pitFiringItems.remove(getKey(tileX, tileY));
		} else {
			pitFiringItems.put(getKey(tileX, tileY), copyFiringItems(items));
		}
	}

	public List<StoredFiringItem> removeFiringItems(int tileX, int tileY) {
		List<StoredFiringItem> items = pitFiringItems.remove(getKey(tileX, tileY));
		return items == null ? new ArrayList<>() : items;
	}

	public List<StoredFiringItem> getFiringItems(int tileX, int tileY) {
		List<StoredFiringItem> items = pitFiringItems.get(getKey(tileX, tileY));
		return items == null ? new ArrayList<>() : copyFiringItems(items);
	}

	public void startBurn(int tileX, int tileY, long burnEndWorldTime) {
		burnEndWorldTimes.put(getKey(tileX, tileY), burnEndWorldTime);
	}

	public void igniteCharcoalPit(int tileX, int tileY, long burnEndWorldTime) {
		startBurn(tileX, tileY, burnEndWorldTime);
		int burningTileID = TileRegistry.getTileID(BurningCharcoalPitTile.stringID);
		level.setTile(tileX, tileY, burningTileID);
		level.sendTileUpdatePacket(tileX, tileY);
		level.getLevelTile(tileX, tileY).checkAround();
		level.getLevelObject(tileX, tileY).checkAround();
	}

	public void removeBurn(int tileX, int tileY) {
		burnEndWorldTimes.remove(getKey(tileX, tileY));
	}

	public void startFiringBurn(int tileX, int tileY, long burnEndWorldTime) {
		firingBurnEndWorldTimes.put(getKey(tileX, tileY), burnEndWorldTime);
	}

	public void igniteFiringPit(int tileX, int tileY, long burnEndWorldTime) {
		startFiringBurn(tileX, tileY, burnEndWorldTime);
		int burningTileID = TileRegistry.getTileID(BurningFiringPitTile.stringID);
		level.setTile(tileX, tileY, burningTileID);
		level.sendTileUpdatePacket(tileX, tileY);
		level.getLevelTile(tileX, tileY).checkAround();
		level.getLevelObject(tileX, tileY).checkAround();
	}

	public void removeFiringBurn(int tileX, int tileY) {
		firingBurnEndWorldTimes.remove(getKey(tileX, tileY));
	}

	public long getFiringBurnEndWorldTime(int tileX, int tileY) {
		return firingBurnEndWorldTimes.getOrDefault(getKey(tileX, tileY), 0L);
	}

	@Override
	public void tick() {
		if (!isServer() || (burnEndWorldTimes.isEmpty() && firingBurnEndWorldTimes.isEmpty())) {
			return;
		}

		long currentWorldTime = level.getWorldEntity().getWorldTime();
		tickCharcoalBurns(currentWorldTime);
		tickFiringBurns(currentWorldTime);
	}

	private void tickCharcoalBurns(long currentWorldTime) {
		if (burnEndWorldTimes.isEmpty()) {
			return;
		}

		int burningTileID = TileRegistry.getTileID(BurningCharcoalPitTile.stringID);
		Iterator<Map.Entry<Long, Long>> iterator = burnEndWorldTimes.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<Long, Long> entry = iterator.next();
			int tileX = (int)(entry.getKey() >> 32);
			int tileY = (int)(long)entry.getKey();

			if (!level.isTileWithinBounds(tileX, tileY) || level.getTileID(tileX, tileY) != burningTileID) {
				iterator.remove();
				pitLogs.remove(entry.getKey());
				continue;
			}

			if (currentWorldTime < entry.getValue()) {
				continue;
			}

			iterator.remove();
			pitLogs.remove(entry.getKey());
			level.setTile(tileX, tileY, TileRegistry.getTileID(ShallowHoleTile.stringID));
			level.sendTileUpdatePacket(tileX, tileY);
			level.getLevelTile(tileX, tileY).checkAround();
			level.getLevelObject(tileX, tileY).checkAround();

			InventoryItem charcoal = new InventoryItem("charcoal", 32);
			ItemPickupEntity pickup = charcoal.getPickupEntity(level, tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F);
			level.entityManager.pickups.add(pickup);
			setPendingCleanup(tileX, tileY, pickup);
			Logging.logMessage(
					"[CharcoalCleanup] Burn completed at " + tileX + "," + tileY
							+ "; spawned charcoal pickup amount=" + pickup.item.getAmount()
							+ ", removed=" + pickup.removed()
							+ ", available=" + pickup.getAvailableAmount()
			);
			level.jobsLayer.addJob(new CharcoalCleanupLevelJob(tileX, tileY, pickup));
			Logging.logMessage("[CharcoalCleanup] Added cleanup level job at " + tileX + "," + tileY);
		}
	}

	private void tickFiringBurns(long currentWorldTime) {
		if (firingBurnEndWorldTimes.isEmpty()) return;

		int burningTileID = TileRegistry.getTileID(BurningFiringPitTile.stringID);
		Iterator<Map.Entry<Long, Long>> iterator = firingBurnEndWorldTimes.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<Long, Long> entry = iterator.next();
			int tileX = (int)(entry.getKey() >> 32);
			int tileY = (int)(long)entry.getKey();

			if (!level.isTileWithinBounds(tileX, tileY) || level.getTileID(tileX, tileY) != burningTileID) {
				iterator.remove();
				pitLogs.remove(entry.getKey());
				pitFiringItems.remove(entry.getKey());
				ClayFiringAutomationLevelData automation = ClayFiringAutomationLevelData.get(level, false);
				if (automation != null) automation.clearAutomatedBurn(tileX, tileY);
				continue;
			}

			if (currentWorldTime < entry.getValue()) continue;

			iterator.remove();
			pitLogs.remove(entry.getKey());
			List<StoredFiringItem> firingItems = pitFiringItems.remove(entry.getKey());
			level.setTile(tileX, tileY, TileRegistry.getTileID(ShallowHoleTile.stringID));
			level.sendTileUpdatePacket(tileX, tileY);
			level.getLevelTile(tileX, tileY).checkAround();
			level.getLevelObject(tileX, tileY).checkAround();

			int firedCount = 0;
			if (firingItems == null || firingItems.isEmpty()) {
				ClayFiringAutomationLevelData automation = ClayFiringAutomationLevelData.get(level, false);
				if (automation != null) automation.clearAutomatedBurn(tileX, tileY);
				continue;
			}

			Map<String, Integer> firedAmounts = new LinkedHashMap<>();
			for (StoredFiringItem stored : firingItems) {
				if (!(ItemRegistry.getItem(stored.itemStringID) instanceof FireableMatItem)) continue;
				FireableMatItem unfiredItem = (FireableMatItem)ItemRegistry.getItem(stored.itemStringID);
				String firedStringID = unfiredItem.getFiredItemStringID();
				firedAmounts.put(firedStringID, firedAmounts.getOrDefault(firedStringID, 0) + 1);
				firedCount++;
			}

			ArrayList<ItemPickupEntity> firedPickups = new ArrayList<>();
			for (Map.Entry<String, Integer> fired : firedAmounts.entrySet()) {
				InventoryItem item = new InventoryItem(fired.getKey(), fired.getValue());
				ItemPickupEntity pickup = item.getPickupEntity(level, tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F);
				level.entityManager.pickups.add(pickup);
				firedPickups.add(pickup);
			}
			Logging.logMessage("[ItemFiring] Burn completed at " + tileX + "," + tileY + "; spawned " + firedCount + " fired items");

			ClayFiringAutomationLevelData automation = ClayFiringAutomationLevelData.get(level, false);
			if (automation != null) automation.handleCompletedBurn(tileX, tileY, firedPickups);
		}
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		save.addInt("produceUntilUnitsStocked", produceUntilUnitsStocked);
		save.addBoolean("repeatForever", repeatForever);

		for (Map.Entry<Long, ProductionRecoveryState> entry : productionRecoveryStates.entrySet()) {
			SaveData stateSave = new SaveData("CHARCOAL_PRODUCTION_RECOVERY");
			stateSave.addInt("tileX", (int)(entry.getKey() >> 32));
			stateSave.addInt("tileY", (int)(long)entry.getKey());
			stateSave.addSafeString("stage", entry.getValue().stage.name());
			stateSave.addInt("zoneUniqueID", entry.getValue().zoneUniqueID);
			save.addSaveData(stateSave);
		}

		for (Map.Entry<Long, Integer> entry : pendingCleanupPickupIDs.entrySet()) {
			SaveData cleanupSave = new SaveData("CHARCOAL_CLEANUP_RECOVERY");
			cleanupSave.addInt("tileX", (int)(entry.getKey() >> 32));
			cleanupSave.addInt("tileY", (int)(long)entry.getKey());
			cleanupSave.addInt("pickupUniqueID", entry.getValue());
			save.addSaveData(cleanupSave);
		}
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		pitLogs.clear();
		pitFiringItems.clear();
		burnEndWorldTimes.clear();
		firingBurnEndWorldTimes.clear();
		productionRecoveryStates.clear();
		pendingCleanupPickupIDs.clear();
		produceUntilUnitsStocked = Math.max(0, save.getInt("produceUntilUnitsStocked", 0, false));
		repeatForever = save.getBoolean("repeatForever", false, false);

		for (LoadData stateSave : save.getLoadDataByName("CHARCOAL_PRODUCTION_RECOVERY")) {
			int tileX = stateSave.getInt("tileX", 0, false);
			int tileY = stateSave.getInt("tileY", 0, false);
			String stageName = stateSave.getSafeString("stage", "", false);
			int zoneUniqueID = stateSave.getInt("zoneUniqueID", 0, false);
			try {
				ProductionStage stage = ProductionStage.valueOf(stageName);
				productionRecoveryStates.put(getKey(tileX, tileY), new ProductionRecoveryState(stage, zoneUniqueID));
			} catch (IllegalArgumentException ignored) {
			}
		}

		for (LoadData cleanupSave : save.getLoadDataByName("CHARCOAL_CLEANUP_RECOVERY")) {
			int tileX = cleanupSave.getInt("tileX", 0, false);
			int tileY = cleanupSave.getInt("tileY", 0, false);
			int pickupUniqueID = cleanupSave.getInt("pickupUniqueID", 0, false);
			pendingCleanupPickupIDs.put(getKey(tileX, tileY), pickupUniqueID);
		}
	}

	@Override
	public void addRegionSaveData(Region region, SaveData save) {
		Set<Long> pitKeys = new HashSet<>(pitLogs.keySet());
		pitKeys.addAll(pitFiringItems.keySet());
		pitKeys.addAll(burnEndWorldTimes.keySet());
		pitKeys.addAll(firingBurnEndWorldTimes.keySet());

		for (Long pitKey : pitKeys) {
			int tileX = (int)(pitKey >> 32);
			int tileY = (int)(long)pitKey;
			if (!isInsideRegion(region, tileX, tileY)) continue;

			SaveData pit = new SaveData("PIT");
			pit.addInt("tileX", tileX);
			pit.addInt("tileY", tileY);

			Long burnEndWorldTime = burnEndWorldTimes.get(pitKey);
			if (burnEndWorldTime != null) pit.addLong("burnEndWorldTime", burnEndWorldTime);

			Long firingBurnEndWorldTime = firingBurnEndWorldTimes.get(pitKey);
			if (firingBurnEndWorldTime != null) pit.addLong("firingBurnEndWorldTime", firingBurnEndWorldTime);

			for (StoredLog log : pitLogs.getOrDefault(pitKey, new ArrayList<>())) {
				SaveData logData = new SaveData("LOG");
				logData.addSafeString("itemStringID", log.itemStringID);
				logData.addInt("amount", log.amount);
				pit.addSaveData(logData);
			}

			for (StoredFiringItem item : pitFiringItems.getOrDefault(pitKey, new ArrayList<>())) {
				SaveData itemData = new SaveData("FIRING_ITEM");
				itemData.addSafeString("itemStringID", item.itemStringID);
				pit.addSaveData(itemData);
			}

			save.addSaveData(pit);
		}
	}

	@Override
	public void loadRegionSaveData(Region region, LoadData save) {
		clearRegionPitState(region);

		for (LoadData pit : save.getLoadDataByName("PIT")) {
			int tileX = pit.getInt("tileX", 0, false);
			int tileY = pit.getInt("tileY", 0, false);
			if (!isInsideRegion(region, tileX, tileY)) continue;

			long key = getKey(tileX, tileY);
			List<StoredLog> logs = new ArrayList<>();
			List<StoredFiringItem> firingItems = new ArrayList<>();

			for (LoadData logData : pit.getLoadDataByName("LOG")) {
				String itemStringID = logData.getSafeString("itemStringID", "", false);
				int amount = logData.getInt("amount", 0, false);
				if (!itemStringID.isEmpty() && amount > 0) logs.add(new StoredLog(itemStringID, amount));
			}

			for (LoadData itemData : pit.getLoadDataByName("FIRING_ITEM")) {
				String itemStringID = itemData.getSafeString("itemStringID", "", false);
				if (!itemStringID.isEmpty()) firingItems.add(new StoredFiringItem(itemStringID));
			}

			if (!logs.isEmpty()) pitLogs.put(key, logs);
			if (!firingItems.isEmpty()) pitFiringItems.put(key, firingItems);

			long burnEndWorldTime = pit.getLong("burnEndWorldTime", 0L, false);
			if (burnEndWorldTime > 0L) burnEndWorldTimes.put(key, burnEndWorldTime);

			long firingBurnEndWorldTime = pit.getLong("firingBurnEndWorldTime", 0L, false);
			if (firingBurnEndWorldTime > 0L) firingBurnEndWorldTimes.put(key, firingBurnEndWorldTime);
		}
	}

	@Override
	public void onUnloadedRegion(Region region) {
		clearRegionPitState(region);
	}

	@Override
	public void onRegionLoaded(Region region) {
		if (!isServer() || !level.isLoadingComplete()) return;
		validateRegionBurnState(region);
		recoverFiringState(region);
	}

	private void clearRegionPitState(Region region) {
		pitLogs.keySet().removeIf(key -> isInsideRegion(region, key));
		pitFiringItems.keySet().removeIf(key -> isInsideRegion(region, key));
		burnEndWorldTimes.keySet().removeIf(key -> isInsideRegion(region, key));
		firingBurnEndWorldTimes.keySet().removeIf(key -> isInsideRegion(region, key));
	}

	private boolean isInsideRegion(Region region, long key) {
		return isInsideRegion(region, (int)(key >> 32), (int)key);
	}

	private boolean isInsideRegion(Region region, int tileX, int tileY) {
		return tileX >= region.tileXOffset && tileX < region.tileXOffset + region.tileWidth
				&& tileY >= region.tileYOffset && tileY < region.tileYOffset + region.tileHeight;
	}

	private void validateRegionBurnState(Region region) {
		int burningCharcoalID = TileRegistry.getTileID(BurningCharcoalPitTile.stringID);
		int burningFiringID = TileRegistry.getTileID(BurningFiringPitTile.stringID);
		int shallowHoleID = TileRegistry.getTileID(ShallowHoleTile.stringID);

		for (int tileX = region.tileXOffset; tileX < region.tileXOffset + region.tileWidth; tileX++) {
			for (int tileY = region.tileYOffset; tileY < region.tileYOffset + region.tileHeight; tileY++) {
				int tileID = level.getTileID(tileX, tileY);
				long key = getKey(tileX, tileY);

				if (tileID == burningCharcoalID) {
					boolean valid = burnEndWorldTimes.containsKey(key) && !pitLogs.getOrDefault(key, new ArrayList<>()).isEmpty();
					if (!valid) {
						Logging.logMessage("[PitPersistence] Orphan burning charcoal pit at " + tileX + "," + tileY + "; extinguishing");
						clearPitState(tileX, tileY);
						level.setTile(tileX, tileY, shallowHoleID);
						level.sendTileUpdatePacket(tileX, tileY);
					}
				} else if (tileID == burningFiringID) {
					boolean valid = firingBurnEndWorldTimes.containsKey(key) && !pitFiringItems.getOrDefault(key, new ArrayList<>()).isEmpty();
					if (!valid) {
						Logging.logMessage("[PitPersistence] Orphan burning firing pit at " + tileX + "," + tileY + "; extinguishing");
						clearPitState(tileX, tileY);
						ClayFiringAutomationLevelData automation = ClayFiringAutomationLevelData.get(level, false);
						if (automation != null) automation.clearAutomatedBurn(tileX, tileY);
						level.setTile(tileX, tileY, shallowHoleID);
						level.sendTileUpdatePacket(tileX, tileY);
					}
				}
			}
		}
	}

	private void clearPitState(int tileX, int tileY) {
		long key = getKey(tileX, tileY);
		pitLogs.remove(key);
		pitFiringItems.remove(key);
		burnEndWorldTimes.remove(key);
		firingBurnEndWorldTimes.remove(key);
	}

	@Override
	public void onLoadingComplete() {
		super.onLoadingComplete();
		if (!isServer()) {
			return;
		}

		level.regionManager.forEachLoadedRegions(this::validateRegionBurnState);
		recoverFiringState();
		recoverProductionJobs();
		recoverCleanupJobs();
	}

	private void recoverFiringState() {
		if (firingBurnEndWorldTimes.isEmpty()) return;
		tickFiringBurns(level.getWorldEntity().getWorldTime());
	}

	private void recoverFiringState(Region region) {
		if (firingBurnEndWorldTimes.keySet().stream().noneMatch(key -> isInsideRegion(region, key))) return;
		tickFiringBurns(level.getWorldEntity().getWorldTime());
	}

	private void recoverProductionJobs() {
		for (Map.Entry<Long, ProductionRecoveryState> entry : new ArrayList<>(productionRecoveryStates.entrySet())) {
			long key = entry.getKey();
			int tileX = (int)(key >> 32);
			int tileY = (int)key;
			ProductionRecoveryState state = entry.getValue();

			if (!level.isTileWithinBounds(tileX, tileY)) {
				productionRecoveryStates.remove(key);
				continue;
			}

			int tileID = level.getTileID(tileX, tileY);
			if (tileID == TileRegistry.getTileID(BurningCharcoalPitTile.stringID)) {
				productionRecoveryStates.remove(key);
				continue;
			}

			ProductionStage physicalStage = getPhysicalProductionStage(tileX, tileY);
			if (physicalStage == null) {
				recoverAbandonedPit(tileX, tileY);
				productionRecoveryStates.remove(key);
				continue;
			}

			CharcoalProductionZone zone = findProductionZone(tileX, tileY, state.zoneUniqueID);
			if (zone == null) {
				recoverAbandonedPit(tileX, tileY);
				productionRecoveryStates.remove(key);
				continue;
			}

			productionRecoveryStates.put(key, new ProductionRecoveryState(physicalStage, zone.getUniqueID()));
			level.jobsLayer.addJob(new CharcoalProductionLevelJob(tileX, tileY, zone, true, physicalStage));
			Logging.logMessage("[CharcoalRecovery] Restored production job at " + tileX + "," + tileY + " from stage " + physicalStage);
		}
	}

	private void recoverCleanupJobs() {
		for (Map.Entry<Long, Integer> entry : new ArrayList<>(pendingCleanupPickupIDs.entrySet())) {
			long key = entry.getKey();
			int tileX = (int)(key >> 32);
			int tileY = (int)key;

			if (!level.isTileWithinBounds(tileX, tileY)
					|| level.getTileID(tileX, tileY) != TileRegistry.getTileID(ShallowHoleTile.stringID)) {
				pendingCleanupPickupIDs.remove(key);
				continue;
			}

			boolean cleanupJobExists = level.jobsLayer.streamJobsInTile(tileX, tileY)
					.anyMatch(job -> job instanceof CharcoalCleanupLevelJob);
			if (cleanupJobExists) {
				continue;
			}

			ItemPickupEntity pickup = resolvePickup(entry.getValue());
			level.jobsLayer.addJob(new CharcoalCleanupLevelJob(tileX, tileY, pickup, entry.getValue()));
			Logging.logMessage("[CharcoalRecovery] Restored cleanup job at " + tileX + "," + tileY);
		}
	}

	private ProductionStage getPhysicalProductionStage(int tileX, int tileY) {
		int tileID = level.getTileID(tileX, tileY);
		if (tileID == TileRegistry.getTileID(CoveredCharcoalPitTile.stringID) && !getLogs(tileX, tileY).isEmpty()) {
			return ProductionStage.COVERED;
		}
		if (tileID == TileRegistry.getTileID(CharcoalPitTile.stringID) && !getLogs(tileX, tileY).isEmpty()) {
			return ProductionStage.LOADED;
		}
		if (tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)) {
			return ProductionStage.DUG;
		}
		return null;
	}

	private CharcoalProductionZone findProductionZone(int tileX, int tileY, int preferredZoneUniqueID) {
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(level)
				.getOrLoadServerDataAtTile(level.getIdentifier(), tileX, tileY);
		if (settlement == null) {
			return null;
		}

		if (preferredZoneUniqueID != 0) {
			Object preferred = settlement.getWorkZones().getZones().get(preferredZoneUniqueID);
			if (preferred instanceof CharcoalProductionZone) {
				CharcoalProductionZone zone = (CharcoalProductionZone)preferred;
				if (!zone.isRemoved() && zone.containsTile(tileX, tileY)) {
					return zone;
				}
			}
		}

		for (Object value : settlement.getWorkZones().getZones().values()) {
			SettlementWorkZone workZone = (SettlementWorkZone)value;
			if (workZone instanceof CharcoalProductionZone
					&& !workZone.isRemoved()
					&& workZone.containsTile(tileX, tileY)) {
				return (CharcoalProductionZone)workZone;
			}
		}
		return null;
	}

	private void recoverAbandonedPit(int tileX, int tileY) {
		removeBurn(tileX, tileY);
		List<StoredLog> logs = removeLogs(tileX, tileY);
		for (StoredLog log : logs) {
			if (log.amount > 0) {
				InventoryItem item = new InventoryItem(log.itemStringID, log.amount);
				level.entityManager.pickups.add(item.getPickupEntity(level, tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F));
			}
		}

		int tileID = level.getTileID(tileX, tileY);
		if (tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)
				|| tileID == TileRegistry.getTileID(CharcoalPitTile.stringID)
				|| tileID == TileRegistry.getTileID(CoveredCharcoalPitTile.stringID)) {
			level.setTile(tileX, tileY, ShallowHoleSystem.getFillTileID(level, tileX, tileY));
			level.sendTileUpdatePacket(tileX, tileY);
			level.getLevelTile(tileX, tileY).checkAround();
			level.getLevelObject(tileX, tileY).checkAround();
		}
	}

	private ItemPickupEntity resolvePickup(int uniqueID) {
		if (uniqueID == 0) {
			return null;
		}
		Object pickup = level.entityManager.pickups.get(uniqueID, false);
		return pickup instanceof ItemPickupEntity ? (ItemPickupEntity)pickup : null;
	}

	private static long getKey(int tileX, int tileY) {
		return ((long)tileX << 32) | (tileY & 0xffffffffL);
	}

	private static List<StoredLog> copyLogs(List<StoredLog> logs) {
		List<StoredLog> copy = new ArrayList<>();
		if (logs != null) {
			for (StoredLog log : logs) {
				copy.add(new StoredLog(log.itemStringID, log.amount));
			}
		}
		return copy;
	}

	private static List<StoredFiringItem> copyFiringItems(List<StoredFiringItem> items) {
		List<StoredFiringItem> copy = new ArrayList<>();
		if (items != null) {
			for (StoredFiringItem item : items) copy.add(new StoredFiringItem(item.itemStringID));
		}
		return copy;
	}

	public static class ProductionRecoveryState {
		public final ProductionStage stage;
		public final int zoneUniqueID;

		public ProductionRecoveryState(ProductionStage stage, int zoneUniqueID) {
			this.stage = stage;
			this.zoneUniqueID = zoneUniqueID;
		}
	}

	public static class StoredFiringItem {
		public final String itemStringID;

		public StoredFiringItem(String itemStringID) {
			this.itemStringID = itemStringID;
		}
	}

	public static class StoredLog {
		public final String itemStringID;
		public final int amount;

		public StoredLog(String itemStringID, int amount) {
			this.itemStringID = itemStringID;
			this.amount = amount;
		}
	}
}
