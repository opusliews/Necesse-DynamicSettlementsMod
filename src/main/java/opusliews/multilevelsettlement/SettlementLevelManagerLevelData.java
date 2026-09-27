package opusliews.multilevelsettlement;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.LevelIdentifier;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import opusliews.logging.Logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SettlementLevelManagerLevelData extends LevelData {
	public static final String managerKey = "opusmultilevelsettlementmanager";
	private final ArrayList<Entry> entries = new ArrayList<>();

	public static SettlementLevelManagerLevelData get(Level surfaceLevel, boolean createNewIfNull) {
		if (surfaceLevel == null) return null;
		LevelData existing = surfaceLevel.getLevelData(managerKey);
		if (existing instanceof SettlementLevelManagerLevelData) return (SettlementLevelManagerLevelData)existing;
		if (existing != null) {
			if (Logging.logEnabled) Logging.logMessage("[LevelManager] Level data key collision level=" + surfaceLevel.getIdentifier() + " class=" + existing.getClass().getName());
			return null;
		}
		if (!createNewIfNull) return null;
		SettlementLevelManagerLevelData data = new SettlementLevelManagerLevelData();
		surfaceLevel.addLevelData(managerKey, data);
		if (Logging.logEnabled) Logging.logMessage("[LevelManager] Created level-aware settlement manager data level=" + surfaceLevel.getIdentifier());
		return data;
	}

	public synchronized void setEntry(int settlementUniqueID, EntryType type, LevelIdentifier levelIdentifier, int tileX, int tileY, boolean present) {
		entries.removeIf(entry -> entry.settlementUniqueID == settlementUniqueID && entry.type == type && entry.levelIdentifier.equals(levelIdentifier) && entry.tileX == tileX && entry.tileY == tileY);
		if (present) entries.add(new Entry(settlementUniqueID, type, levelIdentifier, tileX, tileY));
	}

	public synchronized List<Entry> getEntries(int settlementUniqueID) {
		ArrayList<Entry> result = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.settlementUniqueID == settlementUniqueID) result.add(entry);
		}
		return Collections.unmodifiableList(result);
	}

	public synchronized void clearSettlement(int settlementUniqueID) {
		entries.removeIf(entry -> entry.settlementUniqueID == settlementUniqueID);
	}

	@Override
	public synchronized void addSaveData(SaveData save) {
		super.addSaveData(save);
		for (Entry entry : entries) {
			SaveData entrySave = new SaveData("ENTRY");
			entrySave.addInt("settlementUniqueID", entry.settlementUniqueID);
			entrySave.addSafeString("type", entry.type.name());
			entrySave.addSafeString("level", entry.levelIdentifier.stringID);
			entrySave.addInt("tileX", entry.tileX);
			entrySave.addInt("tileY", entry.tileY);
			save.addSaveData(entrySave);
		}
	}

	@Override
	public synchronized void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		entries.clear();
		for (LoadData entrySave : save.getLoadDataByName("ENTRY")) {
			int settlementUniqueID = entrySave.getInt("settlementUniqueID", -1, false);
			String typeName = entrySave.getSafeString("type", null, false);
			String levelName = entrySave.getSafeString("level", null, false);
			int tileX = entrySave.getInt("tileX", 0, false);
			int tileY = entrySave.getInt("tileY", 0, false);
			if (settlementUniqueID < 0 || typeName == null || levelName == null) continue;
			try {
				EntryType type = EntryType.valueOf(typeName);
				entries.add(new Entry(settlementUniqueID, type, new LevelIdentifier(levelName), tileX, tileY));
			} catch (IllegalArgumentException ignored) {
				if (Logging.logEnabled) Logging.logMessage("[LevelManager] Ignored invalid persisted entry type=" + typeName + " level=" + levelName + " tile=" + tileX + "," + tileY);
			}
		}
		if (Logging.logEnabled && !entries.isEmpty()) Logging.logMessage("[LevelManager] Loaded persisted level-aware manager entries count=" + entries.size() + " level=" + level.getIdentifier());
	}

	public enum EntryType {
		STORAGE,
		WORKSTATION
	}

	public static final class Entry {
		public final int settlementUniqueID;
		public final EntryType type;
		public final LevelIdentifier levelIdentifier;
		public final int tileX;
		public final int tileY;

		public Entry(int settlementUniqueID, EntryType type, LevelIdentifier levelIdentifier, int tileX, int tileY) {
			this.settlementUniqueID = settlementUniqueID;
			this.type = type;
			this.levelIdentifier = levelIdentifier;
			this.tileX = tileX;
			this.tileY = tileY;
		}
	}
}
