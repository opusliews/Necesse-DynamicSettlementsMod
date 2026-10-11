package opusliews.multilevelsettlement;

import necesse.engine.registries.LevelJobRegistry;
import necesse.engine.util.LevelIdentifier;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import necesse.level.maps.levelData.jobs.HasStorageLevelJob;
import necesse.level.maps.levelData.jobs.UseWorkstationLevelJob;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import necesse.level.maps.levelData.settlementData.SettlementWorkstation;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageFoodQualityIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageItemIDIndex;
import necesse.inventory.item.Item;
import opusliews.logging.Logging;
import opusliews.hunger.SettlementFoodAvailabilityCache;
import opusliews.stock.SettlementStockSystem;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class SettlementLevelStorageManager {
	private static final Map<ServerSettlementData, State> states = Collections.synchronizedMap(new WeakHashMap<>());

	private SettlementLevelStorageManager() {
	}

	public static SettlementInventory assignStorage(ServerSettlementData settlement, Level level, int tileX, int tileY) {

		return assignStorage(settlement, level, tileX, tileY, true);

	}

	private static SettlementInventory assignStorage(ServerSettlementData settlement, Level level, int tileX, int tileY, boolean persist) {

		if (!isValidTarget(settlement, level, tileX, tileY, "storage")) return null;
		if (level == settlement.getLevel()) return settlement.storageManager.assignStorage(tileX, tileY, false);

		State state = getState(settlement);
		SettlementLevelPosition key = new SettlementLevelPosition(level.getIdentifier(), tileX, tileY);
		SettlementInventory existing = state.storage.get(key);
		if (existing != null) return existing;

		SettlementLevelInventory created = new SettlementLevelInventory(settlement, level, tileX, tileY);
		if (!created.isTileValid()) {
			if (Logging.logEnabled) Logging.logMessage("[LevelManager] Refused invalid cave storage settlement=" + settlement.uniqueID + " position=" + key);
			return null;
		}
		created.updateAdjacentSolidState();
		state.storage.put(key, created);
		SettlementFoodAvailabilityCache.invalidateTopology(settlement, "level-storage-assigned:" + key);
		if (persist) {
			persistEntry(settlement, SettlementLevelManagerLevelData.EntryType.STORAGE, key, true);
			persistStorageConfig(settlement, created);
		}
		if (Logging.logEnabled) Logging.logMessage("[LevelManager] Registered level-aware storage settlement=" + settlement.uniqueID + " position=" + key);
		return created;

	}

	public static SettlementWorkstation assignWorkstation(ServerSettlementData settlement, Level level, int tileX, int tileY) {

		return assignWorkstation(settlement, level, tileX, tileY, true);

	}

	private static SettlementWorkstation assignWorkstation(ServerSettlementData settlement, Level level, int tileX, int tileY, boolean persist) {

		if (!isValidTarget(settlement, level, tileX, tileY, "workstation")) return null;
		if (opusliews.forge.VanillaForgeWorkstationCleanup.isForge(level, tileX, tileY)) {
			if (Logging.logEnabled) Logging.logMessage("[CraftingForgeJob] Rejected cave vanilla forge workstation assignment tile=" + tileX + "," + tileY);
			return null;
		}
		if (level == settlement.getLevel()) return settlement.storageManager.assignWorkstation(tileX, tileY, false);

		State state = getState(settlement);
		SettlementLevelPosition key = new SettlementLevelPosition(level.getIdentifier(), tileX, tileY);
		SettlementWorkstation existing = state.workstations.get(key);
		if (existing != null) return existing;

		SettlementLevelWorkstation created = new SettlementLevelWorkstation(settlement, level, tileX, tileY);
		if (!created.isTileValid()) {
			if (Logging.logEnabled) Logging.logMessage("[LevelManager] Refused invalid cave workstation settlement=" + settlement.uniqueID + " position=" + key);
			return null;
		}
		created.updateAdjacentSolidState();
		state.workstations.put(key, created);
		if (persist) persistEntry(settlement, SettlementLevelManagerLevelData.EntryType.WORKSTATION, key, true);
		if (Logging.logEnabled) Logging.logMessage("[LevelManager] Registered level-aware workstation settlement=" + settlement.uniqueID + " position=" + key);
		return created;

	}

	public static void removeStorage(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int tileX, int tileY) {

		if (settlement == null || levelIdentifier == null) return;
		if (settlement.getLevel().getIdentifier().equals(levelIdentifier)) {
			settlement.storageManager.removeStorage(tileX, tileY, false);
			return;
		}
		SettlementLevelPosition key = new SettlementLevelPosition(levelIdentifier, tileX, tileY);
		State state = getState(settlement);
		if (state.storage.remove(key) != null) {
			SettlementFoodAvailabilityCache.invalidateTopology(settlement, "level-storage-removed:" + key);
			persistEntry(settlement, SettlementLevelManagerLevelData.EntryType.STORAGE, key, false);
			if (Logging.logEnabled) Logging.logMessage("[LevelManager] Removed level-aware storage settlement=" + settlement.uniqueID + " position=" + key);
		}

	}

	public static void removeWorkstation(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int tileX, int tileY) {

		if (settlement == null || levelIdentifier == null) return;
		if (settlement.getLevel().getIdentifier().equals(levelIdentifier)) {
			settlement.storageManager.removeWorkstation(tileX, tileY, false);
			return;
		}
		SettlementLevelPosition key = new SettlementLevelPosition(levelIdentifier, tileX, tileY);
		State state = getState(settlement);
		if (state.workstations.remove(key) != null) {
			persistEntry(settlement, SettlementLevelManagerLevelData.EntryType.WORKSTATION, key, false);
			if (Logging.logEnabled) Logging.logMessage("[LevelManager] Removed level-aware workstation settlement=" + settlement.uniqueID + " position=" + key);
		}

	}

	public static SettlementInventory getStorage(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int tileX, int tileY) {

		if (settlement == null || levelIdentifier == null) return null;
		if (settlement.getLevel().getIdentifier().equals(levelIdentifier)) return settlement.storageManager.getStorage(tileX, tileY);
		return getState(settlement).storage.get(new SettlementLevelPosition(levelIdentifier, tileX, tileY));

	}

	public static SettlementWorkstation getWorkstation(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int tileX, int tileY) {

		if (settlement == null || levelIdentifier == null) return null;
		if (settlement.getLevel().getIdentifier().equals(levelIdentifier)) return settlement.storageManager.getWorkstation(tileX, tileY);
		return getState(settlement).workstations.get(new SettlementLevelPosition(levelIdentifier, tileX, tileY));

	}

	public static Collection<SettlementInventory> getStorage(ServerSettlementData settlement) {

		if (settlement == null) return Collections.emptyList();
		ArrayList<SettlementInventory> result = new ArrayList<>();
		for (Object value : settlement.storageManager.getStorage()) {
			if (value instanceof SettlementInventory) result.add((SettlementInventory)value);
		}
		result.addAll(getState(settlement).storage.values());
		return result;

	}

	public static Collection<SettlementWorkstation> getWorkstations(ServerSettlementData settlement) {

		if (settlement == null) return Collections.emptyList();
		ArrayList<SettlementWorkstation> result = new ArrayList<>();
		for (Object value : settlement.storageManager.getWorkstations()) {
			if (value instanceof SettlementWorkstation) result.add((SettlementWorkstation)value);
		}
		result.addAll(getState(settlement).workstations.values());
		return result;

	}

	public static ArrayList<Point> getStoragePositions(ServerSettlementData settlement, LevelIdentifier levelIdentifier) {

		ArrayList<Point> result = new ArrayList<>();
		if (settlement == null || levelIdentifier == null) return result;
		if (settlement.getLevel().getIdentifier().equals(levelIdentifier)) {
			for (Object value : settlement.storageManager.getStorage()) {
				if (value instanceof SettlementInventory) {
					SettlementInventory inventory = (SettlementInventory)value;
					result.add(new Point(inventory.tileX, inventory.tileY));
				}
			}
			return result;
		}
		for (Map.Entry<SettlementLevelPosition, SettlementInventory> entry : getState(settlement).storage.entrySet()) {
			if (levelIdentifier.equals(entry.getKey().levelIdentifier)) result.add(new Point(entry.getKey().tileX, entry.getKey().tileY));
		}
		return result;

	}

	public static ArrayList<Point> getWorkstationPositions(ServerSettlementData settlement, LevelIdentifier levelIdentifier) {

		ArrayList<Point> result = new ArrayList<>();
		if (settlement == null || levelIdentifier == null) return result;
		if (settlement.getLevel().getIdentifier().equals(levelIdentifier)) {
			for (Object value : settlement.storageManager.getWorkstations()) {
				if (value instanceof SettlementWorkstation) {
					SettlementWorkstation workstation = (SettlementWorkstation)value;
					result.add(new Point(workstation.tileX, workstation.tileY));
				}
			}
			return result;
		}
		for (Map.Entry<SettlementLevelPosition, SettlementWorkstation> entry : getState(settlement).workstations.entrySet()) {
			if (levelIdentifier.equals(entry.getKey().levelIdentifier)) result.add(new Point(entry.getKey().tileX, entry.getKey().tileY));
		}
		return result;

	}

	public static SettlementStorageRecords getStorageRecords(ServerSettlementData settlement, LevelIdentifier levelIdentifier) {

		if (settlement == null || levelIdentifier == null) return null;
		if (settlement.getLevel().getIdentifier().equals(levelIdentifier)) return settlement.storageRecords;
		State state = getState(settlement);
		SettlementStorageRecords records = state.recordsByLevel.get(levelIdentifier);
		if (records != null) return records;
		if (settlement.getServer() == null) return null;
		Level level = settlement.getServer().world.levelManager.getLevel(levelIdentifier);
		if (level == null) return null;
		records = new SettlementStorageRecords(level);
		state.recordsByLevel.put(levelIdentifier, records);
		return records;

	}

	public static Collection<SettlementStorageRecords> getAllStorageRecords(ServerSettlementData settlement) {

		if (settlement == null) return Collections.emptyList();
		ArrayList<SettlementStorageRecords> result = new ArrayList<>();
		if (settlement.storageRecords != null) result.add(settlement.storageRecords);
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return result;
		LevelIdentifier caveIdentifier = domain.getLevelIdentifier(SettlementLevelType.CAVE);
		if (caveIdentifier == null) return result;
		SettlementStorageRecords caveRecords = getStorageRecords(settlement, caveIdentifier);
		if (caveRecords != null && caveRecords != settlement.storageRecords) result.add(caveRecords);
		return result;

	}

	public static int getTotalStoredItems(ServerSettlementData settlement, Item item) {

		if (settlement == null || item == null) return 0;
		int total = 0;
		for (SettlementStorageRecords records : getAllStorageRecords(settlement)) total += getStoredItemCount(records, item);
		return total;

	}

	public static int getTotalFoodNutrition(ServerSettlementData settlement) {

		if (settlement == null) return 0;
		int total = 0;
		for (SettlementStorageRecords records : getAllStorageRecords(settlement)) total += getFoodNutrition(records);
		return total;

	}

	public static int getTotalFoodItems(ServerSettlementData settlement) {

		if (settlement == null) return 0;
		int total = 0;
		for (SettlementStorageRecords records : getAllStorageRecords(settlement)) total += getFoodItems(records);
		return total;

	}

	private static int getStoredItemCount(SettlementStorageRecords records, Item item) {

		if (records == null) return 0;
		SettlementStorageItemIDIndex index = (SettlementStorageItemIDIndex)records.getIndex(SettlementStorageItemIDIndex.class);
		return index == null ? 0 : index.getTotalItems(item);

	}

	private static int getFoodNutrition(SettlementStorageRecords records) {

		if (records == null) return 0;
		SettlementStorageFoodQualityIndex index = (SettlementStorageFoodQualityIndex)records.getIndex(SettlementStorageFoodQualityIndex.class);
		return index == null ? 0 : index.getTotalNutrition();

	}

	private static int getFoodItems(SettlementStorageRecords records) {

		if (records == null) return 0;
		SettlementStorageFoodQualityIndex index = (SettlementStorageFoodQualityIndex)records.getIndex(SettlementStorageFoodQualityIndex.class);
		return index == null ? 0 : index.getTotalItems();

	}

	/** Keeps non-canonical storage simulations, indexes and HasStorage jobs in sync. */
	public static void tickLevelStorage(ServerSettlementData settlement) {

		if (settlement == null || settlement.getLevel() == null || !settlement.getLevel().isServer()) return;
		restorePersisted(settlement);
		clearInvalids(settlement);
		State state = getState(settlement);
		LinkedHashMap<LevelIdentifier, SettlementStorageRecords> nextRecords = new LinkedHashMap<>();

		for (Map.Entry<SettlementLevelPosition, SettlementInventory> entry : state.storage.entrySet()) {
			SettlementInventory inventory = entry.getValue();
			if (!(inventory instanceof SettlementLevelInventory) || inventory.level == null || !inventory.isStorageValid()) continue;
			SettlementStorageRecords records = nextRecords.computeIfAbsent(inventory.level.getIdentifier(), ignored -> new SettlementStorageRecords(inventory.level));
			((SettlementLevelInventory)inventory).tickLevelStorage(records);

			inventory.level.jobsLayer.streamJobsInTile(inventory.tileX, inventory.tileY)
					.filter(job -> job.getID() == LevelJobRegistry.hasStorageID)
					.forEach(TileLevelJob::remove);
			if (!inventory.isAllAdjacentSolid()) {
				SettlementInventory captured = inventory;
				inventory.level.jobsLayer.addJob(new HasStorageLevelJob(inventory, () -> hasInventory(settlement, captured)), true);
			}
		}

		for (Map.Entry<SettlementLevelPosition, SettlementWorkstation> entry : state.workstations.entrySet()) {
			SettlementWorkstation workstation = entry.getValue();
			if (!(workstation instanceof SettlementLevelWorkstation)) continue;
			SettlementLevelWorkstation levelWorkstation = (SettlementLevelWorkstation)workstation;
			Level workstationLevel = levelWorkstation.getLevel();
			if (workstationLevel == null || !hasWorkstation(settlement, workstation)) continue;
			workstation.updateAdjacentSolidState();
			workstationLevel.jobsLayer.addJob(new UseWorkstationLevelJob(workstation, () -> hasWorkstation(settlement, workstation)), true);
		}

		state.recordsByLevel.clear();
		state.recordsByLevel.putAll(nextRecords);

	}

	public static SettlementLevelPosition getPosition(SettlementInventory inventory) {

		return inventory == null ? null : new SettlementLevelPosition(inventory.level.getIdentifier(), inventory.tileX, inventory.tileY);

	}

	public static SettlementLevelPosition getPosition(SettlementWorkstation workstation) {

		if (workstation == null) return null;
		LevelIdentifier levelIdentifier = workstation instanceof SettlementLevelWorkstation
				? ((SettlementLevelWorkstation)workstation).getLevel().getIdentifier()
				: workstation.data.getLevel().getIdentifier();
		return new SettlementLevelPosition(levelIdentifier, workstation.tileX, workstation.tileY);

	}

	public static boolean hasInventory(ServerSettlementData settlement, SettlementInventory inventory) {

		if (settlement == null || inventory == null) return false;
		if (inventory.level == settlement.getLevel()) return settlement.storageManager.hasInventory(inventory);
		return getState(settlement).storage.get(getPosition(inventory)) == inventory;

	}

	public static boolean hasWorkstation(ServerSettlementData settlement, SettlementWorkstation workstation) {

		if (settlement == null || workstation == null) return false;
		if (!(workstation instanceof SettlementLevelWorkstation)) return settlement.storageManager.hasWorkstation(workstation);
		return getState(settlement).workstations.get(getPosition(workstation)) == workstation;

	}

	public static void persistStorageConfig(ServerSettlementData settlement, SettlementInventory inventory) {

		if (settlement == null || inventory == null || inventory.level == null) return;
		if (inventory.level.getIdentifier().equals(settlement.getLevel().getIdentifier())) return;
		SettlementLevelManagerLevelData data = SettlementLevelManagerLevelData.get(settlement.getLevel(), true);
		if (data != null) data.setStorageConfig(settlement.uniqueID, inventory.level.getIdentifier(), inventory.tileX, inventory.tileY, inventory.priority, inventory.filter, SettlementStockSystem.getTargets(inventory));

	}

	public static void persistWorkstationRecipes(ServerSettlementData settlement, SettlementWorkstation workstation) {

		if (settlement == null || workstation == null || !(workstation instanceof SettlementLevelWorkstation)) return;
		LevelIdentifier levelIdentifier = ((SettlementLevelWorkstation)workstation).getLevel().getIdentifier();
		SettlementLevelManagerLevelData data = SettlementLevelManagerLevelData.get(settlement.getLevel(), true);
		if (data != null) data.setWorkstationRecipes(settlement.uniqueID, levelIdentifier, workstation.tileX, workstation.tileY, workstation.recipes);

	}

	public static void restorePersisted(ServerSettlementData settlement) {

		if (settlement == null || settlement.getLevel() == null) return;
		State state = getState(settlement);
		if (state.restored) return;
		state.restored = true;

		SettlementLevelManagerLevelData levelData = SettlementLevelManagerLevelData.get(settlement.getLevel(), false);
		if (levelData == null) {
			if (Logging.logEnabled) Logging.logMessage("[LevelManager] Initialized level-aware managers settlement=" + settlement.uniqueID + " persistedEntries=0");
			return;
		}
		if (Logging.logEnabled) Logging.logMessage("[LevelManager] Initialized level-aware managers settlement=" + settlement.uniqueID + " persistedEntries=" + levelData.getEntries(settlement.uniqueID).size());
		int restoredStorage = 0;
		int restoredWorkstations = 0;
		for (SettlementLevelManagerLevelData.Entry entry : levelData.getEntries(settlement.uniqueID)) {
			if (entry.levelIdentifier.equals(settlement.getLevel().getIdentifier())) continue;
			Level level = settlement.getServer().world.levelManager.getLevel(entry.levelIdentifier);
			if (level == null) {
				if (Logging.logEnabled) Logging.logMessage("[LevelManager] Deferred persisted entry because level is not loaded settlement=" + settlement.uniqueID + " level=" + entry.levelIdentifier + " tile=" + entry.tileX + "," + entry.tileY);
				state.restored = false;
				continue;
			}
			if (entry.type == SettlementLevelManagerLevelData.EntryType.STORAGE) {
				SettlementInventory restored = assignStorage(settlement, level, entry.tileX, entry.tileY, false);
				if (restored != null) {
					restored.priority = entry.priority;
					if (entry.filter != null) {
						necesse.engine.network.Packet packet = new necesse.engine.network.Packet();
						entry.filter.writePacket(new necesse.engine.network.PacketWriter(packet));
						restored.filter.readPacket(new necesse.engine.network.PacketReader(packet));
					}
					for (Map.Entry<Integer, Integer> stockEntry : entry.stockTargets.entrySet()) {
						SettlementStockSystem.setStockTarget(restored, stockEntry.getKey(), stockEntry.getValue());
					}
					restoredStorage++;
				}
			} else if (entry.type == SettlementLevelManagerLevelData.EntryType.WORKSTATION) {
				SettlementWorkstation workstation = assignWorkstation(settlement, level, entry.tileX, entry.tileY, false);
				if (workstation != null) {
					workstation.recipes.clear();
					if (entry.workstationRecipes != null) workstation.recipes.addAll(entry.workstationRecipes);
					restoredWorkstations++;
				}
			}
		}
		if (Logging.logEnabled && (restoredStorage > 0 || restoredWorkstations > 0)) {
			Logging.logMessage("[LevelManager] Restored level-aware manager entries settlement=" + settlement.uniqueID + " storage=" + restoredStorage + " workstations=" + restoredWorkstations);
		}

	}

	public static void clearInvalids(ServerSettlementData settlement) {

		if (settlement == null) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return;
		State state = getState(settlement);
		ArrayList<SettlementLevelPosition> storageRemoves = new ArrayList<>();
		for (Map.Entry<SettlementLevelPosition, SettlementInventory> entry : state.storage.entrySet()) {
			if (!domain.isTileWithinBounds(entry.getKey()) || !entry.getValue().isTileValid()) storageRemoves.add(entry.getKey());
		}
		for (SettlementLevelPosition key : storageRemoves) removeStorage(settlement, key.levelIdentifier, key.tileX, key.tileY);

		ArrayList<SettlementLevelPosition> workstationRemoves = new ArrayList<>();
		for (Map.Entry<SettlementLevelPosition, SettlementWorkstation> entry : state.workstations.entrySet()) {
			if (!domain.isTileWithinBounds(entry.getKey()) || !entry.getValue().isTileValid()) workstationRemoves.add(entry.getKey());
		}
		for (SettlementLevelPosition key : workstationRemoves) removeWorkstation(settlement, key.levelIdentifier, key.tileX, key.tileY);

	}

	public static void remove(ServerSettlementData settlement) {

		if (settlement == null) return;
		states.remove(settlement);
		SettlementFoodAvailabilityCache.remove(settlement);
		SettlementLevelManagerLevelData levelData = SettlementLevelManagerLevelData.get(settlement.getLevel(), false);
		if (levelData != null) levelData.clearSettlement(settlement.uniqueID);

	}

	private static State getState(ServerSettlementData settlement) {

		return states.computeIfAbsent(settlement, ignored -> new State());

	}

	private static boolean isValidTarget(ServerSettlementData settlement, Level level, int tileX, int tileY, String kind) {

		if (settlement == null || level == null) return false;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null || !domain.isTileWithinBounds(level.getIdentifier(), tileX, tileY)) {
			if (Logging.logEnabled) Logging.logMessage("[LevelManager] Refused " + kind + " outside logical settlement settlement=" + settlement.uniqueID + " level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
			return false;
		}
		return true;

	}

	private static void persistEntry(ServerSettlementData settlement, SettlementLevelManagerLevelData.EntryType type, SettlementLevelPosition position, boolean present) {

		if (settlement == null || position == null) return;
		SettlementLevelManagerLevelData data = SettlementLevelManagerLevelData.get(settlement.getLevel(), true);
		if (data != null) data.setEntry(settlement.uniqueID, type, position.levelIdentifier, position.tileX, position.tileY, present);

	}

	private static final class State {
		final LinkedHashMap<SettlementLevelPosition, SettlementInventory> storage = new LinkedHashMap<>();
		final LinkedHashMap<SettlementLevelPosition, SettlementWorkstation> workstations = new LinkedHashMap<>();
		final LinkedHashMap<LevelIdentifier, SettlementStorageRecords> recordsByLevel = new LinkedHashMap<>();
		boolean restored;
	}
}
