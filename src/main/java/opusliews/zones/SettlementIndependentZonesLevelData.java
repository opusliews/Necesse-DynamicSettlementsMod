package opusliews.zones;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.LevelIdentifier;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import opusliews.logging.Logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SettlementIndependentZonesLevelData extends LevelData {
	public static final String managerKey = "opusindependentsettlementzones";
	private final ArrayList<RestrictEntry> restrictEntries = new ArrayList<>();
	private final ArrayList<WorkEntry> workEntries = new ArrayList<>();

	public static SettlementIndependentZonesLevelData get(Level surfaceLevel, boolean createNewIfNull) {
		if (surfaceLevel == null) return null;
		LevelData existing = surfaceLevel.getLevelData(managerKey);
		if (existing instanceof SettlementIndependentZonesLevelData) return (SettlementIndependentZonesLevelData)existing;
		if (existing != null) {
			if (Logging.logEnabled) Logging.logMessage("[IndependentZones] Level data key collision level=" + surfaceLevel.getIdentifier() + " class=" + existing.getClass().getName());
			return null;
		}
		if (!createNewIfNull) return null;
		SettlementIndependentZonesLevelData data = new SettlementIndependentZonesLevelData();
		surfaceLevel.addLevelData(managerKey, data);
		if (Logging.logEnabled) Logging.logMessage("[IndependentZones] Created level data surface=" + surfaceLevel.getIdentifier());
		return data;
	}

	public synchronized void putRestrict(int settlementUniqueID, LevelIdentifier levelIdentifier, int uniqueID, int index, String saveScript) {
		restrictEntries.removeIf(entry -> entry.settlementUniqueID == settlementUniqueID && entry.uniqueID == uniqueID);
		restrictEntries.add(new RestrictEntry(settlementUniqueID, levelIdentifier, uniqueID, index, saveScript));
	}

	public synchronized void removeRestrict(int settlementUniqueID, int uniqueID) {
		restrictEntries.removeIf(entry -> entry.settlementUniqueID == settlementUniqueID && entry.uniqueID == uniqueID);
	}

	public synchronized RestrictEntry getRestrict(int settlementUniqueID, int uniqueID) {
		for (RestrictEntry entry : restrictEntries) {
			if (entry.settlementUniqueID == settlementUniqueID && entry.uniqueID == uniqueID) return entry.copy();
		}
		return null;
	}

	public synchronized List<RestrictEntry> getRestricts(int settlementUniqueID) {
		ArrayList<RestrictEntry> result = new ArrayList<>();
		for (RestrictEntry entry : restrictEntries) if (entry.settlementUniqueID == settlementUniqueID) result.add(entry.copy());
		return Collections.unmodifiableList(result);
	}

	public synchronized void putWork(int settlementUniqueID, LevelIdentifier levelIdentifier, int zoneID, int uniqueID, String saveScript) {
		workEntries.removeIf(entry -> entry.settlementUniqueID == settlementUniqueID && entry.uniqueID == uniqueID);
		workEntries.add(new WorkEntry(settlementUniqueID, levelIdentifier, zoneID, uniqueID, saveScript));
	}

	public synchronized void removeWork(int settlementUniqueID, int uniqueID) {
		workEntries.removeIf(entry -> entry.settlementUniqueID == settlementUniqueID && entry.uniqueID == uniqueID);
	}

	public synchronized WorkEntry getWork(int settlementUniqueID, int uniqueID) {
		for (WorkEntry entry : workEntries) {
			if (entry.settlementUniqueID == settlementUniqueID && entry.uniqueID == uniqueID) return entry.copy();
		}
		return null;
	}

	public synchronized List<WorkEntry> getWorks(int settlementUniqueID) {
		ArrayList<WorkEntry> result = new ArrayList<>();
		for (WorkEntry entry : workEntries) if (entry.settlementUniqueID == settlementUniqueID) result.add(entry.copy());
		return Collections.unmodifiableList(result);
	}

	public synchronized List<WorkEntry> getWorks(int settlementUniqueID, LevelIdentifier levelIdentifier) {
		ArrayList<WorkEntry> result = new ArrayList<>();
		for (WorkEntry entry : workEntries) {
			if (entry.settlementUniqueID == settlementUniqueID && entry.levelIdentifier.equals(levelIdentifier)) result.add(entry.copy());
		}
		return Collections.unmodifiableList(result);
	}

	public synchronized void clearSettlement(int settlementUniqueID) {
		restrictEntries.removeIf(entry -> entry.settlementUniqueID == settlementUniqueID);
		workEntries.removeIf(entry -> entry.settlementUniqueID == settlementUniqueID);
	}

	@Override
	public synchronized void addSaveData(SaveData save) {
		super.addSaveData(save);
		for (RestrictEntry entry : restrictEntries) {
			SaveData entrySave = new SaveData("RESTRICT");
			entrySave.addInt("settlementUniqueID", entry.settlementUniqueID);
			entrySave.addSafeString("level", entry.levelIdentifier.stringID);
			entrySave.addInt("uniqueID", entry.uniqueID);
			entrySave.addInt("index", entry.index);
			entrySave.addSafeString("data", entry.saveScript);
			save.addSaveData(entrySave);
		}
		for (WorkEntry entry : workEntries) {
			SaveData entrySave = new SaveData("WORK");
			entrySave.addInt("settlementUniqueID", entry.settlementUniqueID);
			entrySave.addSafeString("level", entry.levelIdentifier.stringID);
			entrySave.addInt("zoneID", entry.zoneID);
			entrySave.addInt("uniqueID", entry.uniqueID);
			entrySave.addSafeString("data", entry.saveScript);
			save.addSaveData(entrySave);
		}
	}

	@Override
	public synchronized void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		restrictEntries.clear();
		workEntries.clear();
		for (LoadData entrySave : save.getLoadDataByName("RESTRICT")) {
			try {
				int settlementUniqueID = entrySave.getInt("settlementUniqueID", 0, false);
				String level = entrySave.getSafeString("level", null, false);
				int uniqueID = entrySave.getInt("uniqueID", 0, false);
				int index = entrySave.getInt("index", 0, false);
				String data = entrySave.getSafeString("data", null, false);
				if (settlementUniqueID != 0 && level != null && uniqueID != 0 && data != null) {
					restrictEntries.add(new RestrictEntry(settlementUniqueID, new LevelIdentifier(level), uniqueID, index, data));
				}
			}
			catch (RuntimeException error) {
				if (Logging.logEnabled) Logging.logMessage("[IndependentZones] Ignored invalid restriction entry: " + error.getMessage());
			}
		}
		for (LoadData entrySave : save.getLoadDataByName("WORK")) {
			try {
				int settlementUniqueID = entrySave.getInt("settlementUniqueID", 0, false);
				String level = entrySave.getSafeString("level", null, false);
				int zoneID = entrySave.getInt("zoneID", -1, false);
				int uniqueID = entrySave.getInt("uniqueID", 0, false);
				String data = entrySave.getSafeString("data", null, false);
				if (settlementUniqueID != 0 && level != null && zoneID >= 0 && uniqueID != 0 && data != null) {
					workEntries.add(new WorkEntry(settlementUniqueID, new LevelIdentifier(level), zoneID, uniqueID, data));
				}
			}
			catch (RuntimeException error) {
				if (Logging.logEnabled) Logging.logMessage("[IndependentZones] Ignored invalid work-zone entry: " + error.getMessage());
			}
		}
		if (Logging.logEnabled && (!restrictEntries.isEmpty() || !workEntries.isEmpty())) {
			Logging.logMessage("[IndependentZones] Loaded restrictions=" + restrictEntries.size() + " workZones=" + workEntries.size() + " surface=" + level.getIdentifier());
		}
	}

	public static final class RestrictEntry {
		public final int settlementUniqueID;
		public final LevelIdentifier levelIdentifier;
		public final int uniqueID;
		public final int index;
		public final String saveScript;

		public RestrictEntry(int settlementUniqueID, LevelIdentifier levelIdentifier, int uniqueID, int index, String saveScript) {
			this.settlementUniqueID = settlementUniqueID;
			this.levelIdentifier = levelIdentifier;
			this.uniqueID = uniqueID;
			this.index = index;
			this.saveScript = saveScript;
		}

		public RestrictEntry copy() {
			return new RestrictEntry(settlementUniqueID, levelIdentifier, uniqueID, index, saveScript);
		}
	}

	public static final class WorkEntry {
		public final int settlementUniqueID;
		public final LevelIdentifier levelIdentifier;
		public final int zoneID;
		public final int uniqueID;
		public final String saveScript;

		public WorkEntry(int settlementUniqueID, LevelIdentifier levelIdentifier, int zoneID, int uniqueID, String saveScript) {
			this.settlementUniqueID = settlementUniqueID;
			this.levelIdentifier = levelIdentifier;
			this.zoneID = zoneID;
			this.uniqueID = uniqueID;
			this.saveScript = saveScript;
		}

		public WorkEntry copy() {
			return new WorkEntry(settlementUniqueID, levelIdentifier, zoneID, uniqueID, saveScript);
		}
	}
}
