package opusliews.multilevelsettlement;

import necesse.engine.util.LevelIdentifier;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import necesse.level.maps.levelData.settlementData.SettlementWorkstation;
import opusliews.logging.Logging;

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
		if (persist) persistEntry(settlement, SettlementLevelManagerLevelData.EntryType.STORAGE, key, true);
		if (Logging.logEnabled) Logging.logMessage("[LevelManager] Registered level-aware storage settlement=" + settlement.uniqueID + " position=" + key);
		return created;
	}

	public static SettlementWorkstation assignWorkstation(ServerSettlementData settlement, Level level, int tileX, int tileY) {
		return assignWorkstation(settlement, level, tileX, tileY, true);
	}

	private static SettlementWorkstation assignWorkstation(ServerSettlementData settlement, Level level, int tileX, int tileY, boolean persist) {
		if (!isValidTarget(settlement, level, tileX, tileY, "workstation")) return null;
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
				if (assignStorage(settlement, level, entry.tileX, entry.tileY, false) != null) restoredStorage++;
			} else if (entry.type == SettlementLevelManagerLevelData.EntryType.WORKSTATION) {
				if (assignWorkstation(settlement, level, entry.tileX, entry.tileY, false) != null) restoredWorkstations++;
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
		boolean restored;
	}
}
