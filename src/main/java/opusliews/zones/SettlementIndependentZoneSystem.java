package opusliews.zones;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameRandom;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.util.ZoningChange;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.RestrictZone;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class SettlementIndependentZoneSystem {
	private static final WeakHashMap<ServerSettlementData, HashMap<LevelIdentifier, LevelScopedWorkZoneManager>> workManagers = new WeakHashMap<>();

	private SettlementIndependentZoneSystem() {
	}

	public static boolean isSurfaceLevel(ServerSettlementData settlement, LevelIdentifier levelIdentifier) {
		return settlement != null && levelIdentifier != null && settlement.networkData != null && settlement.networkData.level != null && settlement.networkData.level.getIdentifier().equals(levelIdentifier);
	}

	public static boolean isDomainLevel(ServerSettlementData settlement, LevelIdentifier levelIdentifier) {
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		return domain != null && levelIdentifier != null && domain.containsLevel(levelIdentifier);
	}

	public static SettlementIndependentZonesLevelData getData(ServerSettlementData settlement, boolean create) {
		return settlement == null || settlement.networkData == null ? null : SettlementIndependentZonesLevelData.get(settlement.networkData.level, create);
	}

	public static synchronized LevelScopedWorkZoneManager getWorkManager(ServerSettlementData settlement, LevelIdentifier levelIdentifier, boolean create) {
		if (settlement == null || levelIdentifier == null || isSurfaceLevel(settlement, levelIdentifier) || !isDomainLevel(settlement, levelIdentifier)) return null;
		HashMap<LevelIdentifier, LevelScopedWorkZoneManager> managers = workManagers.computeIfAbsent(settlement, ignored -> new HashMap<>());
		LevelScopedWorkZoneManager existing = managers.get(levelIdentifier);
		if (existing != null) return existing;
		if (!create) return null;
		Level level = settlement.getServer().world.levelManager.getLevel(levelIdentifier);
		if (level == null) {
			SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
			SettlementLevelType type = domain == null ? null : domain.getLevelType(levelIdentifier);
			if (type != null) level = domain.getLoadedLevel(type);
		}
		if (level == null) return null;
		LevelScopedWorkZoneManager manager = new LevelScopedWorkZoneManager(settlement, level);
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		if (data != null) manager.loadEntries(data.getWorks(settlement.uniqueID, levelIdentifier));
		managers.put(levelIdentifier, manager);
		return manager;
	}

	public static synchronized LevelScopedWorkZoneManager getWorkManagerByZone(ServerSettlementData settlement, int uniqueID, boolean create) {
		LevelIdentifier owner = getWorkZoneLevel(settlement, uniqueID);
		return owner == null || isSurfaceLevel(settlement, owner) ? null : getWorkManager(settlement, owner, create);
	}

	public static LevelIdentifier getWorkZoneLevel(ServerSettlementData settlement, int uniqueID) {
		if (settlement == null || uniqueID == 0) return null;
		if (settlement.getWorkZones().getZones().containsKey(uniqueID)) return settlement.networkData.level.getIdentifier();
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		SettlementIndependentZonesLevelData.WorkEntry entry = data == null ? null : data.getWork(settlement.uniqueID, uniqueID);
		return entry == null ? null : entry.levelIdentifier;
	}

	public static Collection<SettlementWorkZone> getAllCustomWorkZones(ServerSettlementData settlement) {
		ArrayList<SettlementWorkZone> result = new ArrayList<>();
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		if (data == null) return result;
		HashSet<LevelIdentifier> levels = new HashSet<>();
		for (SettlementIndependentZonesLevelData.WorkEntry entry : data.getWorks(settlement.uniqueID)) levels.add(entry.levelIdentifier);
		for (LevelIdentifier level : levels) {
			LevelScopedWorkZoneManager manager = getWorkManager(settlement, level, true);
			if (manager != null) for (Object value : manager.getZones().values()) result.add((SettlementWorkZone)value);
		}
		return result;
	}


	public static SettlementWorkZone getCustomWorkZone(ServerSettlementData settlement, int uniqueID) {
		if (settlement == null || uniqueID == 0) return null;
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		SettlementIndependentZonesLevelData.WorkEntry entry = data == null ? null : data.getWork(settlement.uniqueID, uniqueID);
		if (entry == null) return null;
		LevelScopedWorkZoneManager manager = getWorkManager(settlement, entry.levelIdentifier, true);
		return manager == null ? null : manager.getZone(uniqueID);
	}

	public static SettlementWorkZone getWorkZone(ServerSettlementData settlement, int uniqueID) {
		SettlementWorkZone surface = settlement == null ? null : (SettlementWorkZone)settlement.getWorkZones().getZones().get(uniqueID);
		if (surface != null) return surface;
		return getCustomWorkZone(settlement, uniqueID);
	}

	public static SettlementWorkZone createWorkZone(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int zoneID, int uniqueID, Rectangle rectangle, Point anchor) {
		LevelScopedWorkZoneManager manager = getWorkManager(settlement, levelIdentifier, true);
		if (manager == null || isUniqueIDUsed(settlement, uniqueID)) return null;
		SettlementWorkZone zone = manager.createLevelZone(zoneID, uniqueID, rectangle, anchor);
		if (zone != null) manager.persist(zone);
		return zone;
	}

	public static SettlementWorkZone expandWorkZone(ServerSettlementData settlement, int uniqueID, LevelIdentifier editingLevel, Rectangle rectangle, Point anchor) {
		LevelIdentifier owner = getWorkZoneLevel(settlement, uniqueID);
		if (owner == null || editingLevel == null || !owner.equals(editingLevel) || isSurfaceLevel(settlement, owner)) return null;
		LevelScopedWorkZoneManager manager = getWorkManager(settlement, owner, true);
		SettlementWorkZone zone = manager == null ? null : manager.expandLevelZone(uniqueID, rectangle, anchor);
		if (zone != null) manager.persist(zone);
		return zone;
	}

	public static SettlementWorkZone shrinkWorkZone(ServerSettlementData settlement, int uniqueID, LevelIdentifier editingLevel, Rectangle rectangle) {
		LevelIdentifier owner = getWorkZoneLevel(settlement, uniqueID);
		if (owner == null || editingLevel == null || !owner.equals(editingLevel) || isSurfaceLevel(settlement, owner)) return null;
		LevelScopedWorkZoneManager manager = getWorkManager(settlement, owner, true);
		if (manager == null) return null;
		SettlementWorkZone zone = manager.shrinkLevelZone(uniqueID, rectangle);
		if (zone == null) removePersistedWork(settlement, uniqueID);
		else manager.persist(zone);
		return zone;
	}

	public static boolean deleteWorkZone(ServerSettlementData settlement, int uniqueID) {
		LevelIdentifier owner = getWorkZoneLevel(settlement, uniqueID);
		if (owner == null || isSurfaceLevel(settlement, owner)) return false;
		LevelScopedWorkZoneManager manager = getWorkManager(settlement, owner, true);
		if (manager == null || manager.removeLevelZone(uniqueID) == null) return false;
		removePersistedWork(settlement, uniqueID);
		return true;
	}

	public static void persistWorkZone(ServerSettlementData settlement, LevelIdentifier levelIdentifier, SettlementWorkZone zone) {
		if (settlement == null || levelIdentifier == null || zone == null || isSurfaceLevel(settlement, levelIdentifier)) return;
		SettlementIndependentZonesLevelData data = getData(settlement, true);
		if (data == null) return;
		SaveData save = new SaveData("DATA");
		zone.addSaveData(save);
		data.putWork(settlement.uniqueID, levelIdentifier, zone.getID(), zone.getUniqueID(), save.getScript());
	}

	public static void removePersistedWork(ServerSettlementData settlement, int uniqueID) {
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		if (data == null) return;
		SettlementIndependentZonesLevelData.WorkEntry previous = data.getWork(settlement.uniqueID, uniqueID);
		data.removeWork(settlement.uniqueID, uniqueID);
		if (previous != null && Logging.logEnabled) Logging.logMessage("[IndependentZonesDebug] Removed persisted work zone settlement="
				+ settlement.uniqueID + " uniqueID=" + uniqueID + " owner=" + previous.levelIdentifier
				+ " zoneID=" + previous.zoneID);
	}

	public static void tickSecond(ServerSettlementData settlement) {
		for (LevelScopedWorkZoneManager manager : getManagersForPersistedLevels(settlement)) manager.tickSecondLevel();
	}

	public static void tickJobs(ServerSettlementData settlement) {
		for (LevelScopedWorkZoneManager manager : getManagersForPersistedLevels(settlement)) manager.tickJobsLevel();
	}

	private static List<LevelScopedWorkZoneManager> getManagersForPersistedLevels(ServerSettlementData settlement) {
		ArrayList<LevelScopedWorkZoneManager> result = new ArrayList<>();
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		if (data == null) return result;
		HashSet<LevelIdentifier> levels = new HashSet<>();
		for (SettlementIndependentZonesLevelData.WorkEntry entry : data.getWorks(settlement.uniqueID)) levels.add(entry.levelIdentifier);
		for (LevelIdentifier level : levels) {
			LevelScopedWorkZoneManager manager = getWorkManager(settlement, level, true);
			if (manager != null) result.add(manager);
		}
		return result;
	}

	public static RestrictZone createRestrictZone(ServerSettlementData settlement, LevelIdentifier levelIdentifier) {
		if (settlement == null || levelIdentifier == null || !isDomainLevel(settlement, levelIdentifier)) return null;
		List<RestrictZone> allZones = getAllRestrictZones(settlement);
		if (allZones.size() >= ServerSettlementData.MAX_RESTRICT_ZONES) return null;

		int index = allZones.stream().mapToInt(zone -> zone.index).max().orElse(-1) + 1;
		int number = allZones.size() + 1;
		GameMessage name = new LocalMessage("ui", "settlementareadefname", new Object[]{"number", number});
		while (containsRestrictName(allZones, name.translate())) {
			number++;
			name = new LocalMessage("ui", "settlementareadefname", new Object[]{"number", number});
		}

		RestrictZone zone;
		if (isSurfaceLevel(settlement, levelIdentifier)) {
			zone = settlement.addNewRestrictZone();
			if (zone == null) return null;
			zone.index = index;
			zone.name = name;
			zone.colorHue = (index + 1) * 3 * 36 % 360;
			return zone;
		}

		SettlementIndependentZonesLevelData data = getData(settlement, true);
		if (data == null) return null;
		int uniqueID = nextUniqueID(settlement);
		zone = new RestrictZone(settlement, uniqueID, index, name);
		zone.colorHue = (index + 1) * 3 * 36 % 360;
		persistRestrict(settlement, levelIdentifier, zone);
		return zone;
	}

	public static RestrictZone cloneRestrictZone(ServerSettlementData settlement, int uniqueID) {
		LevelIdentifier owner = getRestrictZoneLevel(settlement, uniqueID);
		if (owner == null) return null;
		RestrictZone oldZone = getRestrictZone(settlement, uniqueID);
		if (oldZone == null || getAllRestrictZones(settlement).size() >= ServerSettlementData.MAX_RESTRICT_ZONES) return null;
		RestrictZone copy = createRestrictZone(settlement, owner);
		if (copy == null) return null;
		copy.copyZoneFrom(oldZone);
		if (!isSurfaceLevel(settlement, owner)) persistRestrict(settlement, owner, copy);
		return copy;
	}

	public static boolean changeRestrictZone(ServerSettlementData settlement, int uniqueID, LevelIdentifier editingLevel, ZoningChange change) {
		LevelIdentifier owner = getRestrictZoneLevel(settlement, uniqueID);
		if (owner == null || editingLevel == null || !owner.equals(editingLevel) || isSurfaceLevel(settlement, owner)) return false;
		RestrictZone zone = getRestrictZone(settlement, uniqueID);
		if (zone == null || !zone.applyChange(change)) return false;
		persistRestrict(settlement, owner, zone);
		return true;
	}

	public static boolean renameRestrictZone(ServerSettlementData settlement, int uniqueID, String name) {
		LevelIdentifier owner = getRestrictZoneLevel(settlement, uniqueID);
		if (owner == null || isSurfaceLevel(settlement, owner) || name == null || name.isEmpty()) return false;
		RestrictZone zone = getRestrictZone(settlement, uniqueID);
		if (zone == null) return false;
		if (name.length() > 30) name = name.substring(0, 30);
		zone.name = new StaticMessage(name);
		persistRestrict(settlement, owner, zone);
		return true;
	}

	public static boolean recolorRestrictZone(ServerSettlementData settlement, int uniqueID, int hue) {
		LevelIdentifier owner = getRestrictZoneLevel(settlement, uniqueID);
		if (owner == null || isSurfaceLevel(settlement, owner)) return false;
		RestrictZone zone = getRestrictZone(settlement, uniqueID);
		if (zone == null) return false;
		zone.colorHue = Math.floorMod(hue, 360);
		persistRestrict(settlement, owner, zone);
		return true;
	}

	public static boolean deleteRestrictZone(ServerSettlementData settlement, int uniqueID) {
		LevelIdentifier owner = getRestrictZoneLevel(settlement, uniqueID);
		if (owner == null || isSurfaceLevel(settlement, owner)) return false;
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		if (data == null || data.getRestrict(settlement.uniqueID, uniqueID) == null) return false;
		data.removeRestrict(settlement.uniqueID, uniqueID);
		return true;
	}

	public static RestrictZone getRestrictZone(ServerSettlementData settlement, int uniqueID) {
		if (settlement == null || uniqueID == 0) return null;
		RestrictZone surface = settlement.getRestrictZone(uniqueID);
		if (surface != null) return surface;
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		SettlementIndependentZonesLevelData.RestrictEntry entry = data == null ? null : data.getRestrict(settlement.uniqueID, uniqueID);
		if (entry == null) return null;
		try {
			return new RestrictZone(settlement, entry.index, new LoadData(entry.saveScript), 0, 0);
		}
		catch (RuntimeException error) {
			if (Logging.logEnabled) Logging.logMessage("[IndependentZones] Failed loading restriction uniqueID=" + uniqueID + " error=" + error.getMessage());
			return null;
		}
	}

	public static LevelIdentifier getRestrictZoneLevel(ServerSettlementData settlement, int uniqueID) {
		if (settlement == null || uniqueID == 0) return null;
		if (settlement.getRestrictZone(uniqueID) != null) return settlement.networkData.level.getIdentifier();
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		SettlementIndependentZonesLevelData.RestrictEntry entry = data == null ? null : data.getRestrict(settlement.uniqueID, uniqueID);
		return entry == null ? null : entry.levelIdentifier;
	}

	public static List<RestrictZone> getAllCustomRestrictZones(ServerSettlementData settlement) {
		ArrayList<RestrictZone> result = new ArrayList<>();
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		if (data == null) return result;
		for (SettlementIndependentZonesLevelData.RestrictEntry entry : data.getRestricts(settlement.uniqueID)) {
			RestrictZone zone = getRestrictZone(settlement, entry.uniqueID);
			if (zone != null) result.add(zone);
		}
		result.sort(Comparator.comparingInt(zone -> zone.index));
		return result;
	}

	public static List<RestrictZone> getAllRestrictZones(ServerSettlementData settlement) {
		ArrayList<RestrictZone> result = new ArrayList<>();
		if (settlement != null) {
			for (Object value : settlement.getRestrictZones()) result.add((RestrictZone)value);
			result.addAll(getAllCustomRestrictZones(settlement));
		}
		return result;
	}

	public static void persistRestrict(ServerSettlementData settlement, LevelIdentifier levelIdentifier, RestrictZone zone) {
		if (settlement == null || levelIdentifier == null || zone == null || isSurfaceLevel(settlement, levelIdentifier)) return;
		SettlementIndependentZonesLevelData data = getData(settlement, true);
		if (data == null) return;
		SaveData save = new SaveData("DATA");
		zone.addSaveData(save);
		data.putRestrict(settlement.uniqueID, levelIdentifier, zone.uniqueID, zone.index, save.getScript());
	}

	public static boolean isTileInRestriction(ServerSettlementData settlement, int uniqueID, LevelIdentifier levelIdentifier, int tileX, int tileY) {
		if (uniqueID == 0) return true;
		LevelIdentifier owner = getRestrictZoneLevel(settlement, uniqueID);
		if (owner == null || levelIdentifier == null || !owner.equals(levelIdentifier)) return false;
		RestrictZone zone = getRestrictZone(settlement, uniqueID);
		return zone != null && zone.containsTile(tileX, tileY);
	}

	private static boolean containsRestrictName(List<RestrictZone> zones, String name) {
		for (RestrictZone zone : zones) {
			if (zone != null && zone.name != null && zone.name.translate().equals(name)) return true;
		}
		return false;
	}

	public static boolean isUniqueIDUsed(ServerSettlementData settlement, int uniqueID) {
		if (uniqueID == 0 || uniqueID == 1) return true;
		if (settlement.getWorkZones().getZones().containsKey(uniqueID) || settlement.getRestrictZone(uniqueID) != null) return true;
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		return data != null && (data.getWork(settlement.uniqueID, uniqueID) != null || data.getRestrict(settlement.uniqueID, uniqueID) != null);
	}

	private static int nextUniqueID(ServerSettlementData settlement) {
		for (int i = 0; i < 1000; i++) {
			int uniqueID = GameRandom.globalRandom.nextInt();
			if (!isUniqueIDUsed(settlement, uniqueID)) return uniqueID;
		}
		throw new IllegalStateException("Could not allocate independent settlement zone unique ID");
	}

	public static void removeSettlement(ServerSettlementData settlement) {
		if (settlement == null) return;
		SettlementIndependentZonesLevelData data = getData(settlement, false);
		if (data != null) data.clearSettlement(settlement.uniqueID);
		synchronized (SettlementIndependentZoneSystem.class) {
			workManagers.remove(settlement);
		}
	}
}
