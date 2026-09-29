package opusliews.multilevelsettlement;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.LevelIdentifier;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import opusliews.logging.Logging;

public class SettlementLevelZoneLevelData extends LevelData {
	public static final String managerKey = "opusmultilevelsettlementzones";
	private final ArrayList<Entry> entries = new ArrayList<>();

	public static SettlementLevelZoneLevelData get(Level surfaceLevel, boolean createNewIfNull) {
		if (surfaceLevel == null) return null;
		LevelData existing = surfaceLevel.getLevelData(managerKey);
		if (existing instanceof SettlementLevelZoneLevelData) return (SettlementLevelZoneLevelData)existing;
		if (existing != null) {
			if (Logging.logEnabled) Logging.logMessage("[LevelZones] Level data key collision level=" + surfaceLevel.getIdentifier() + " class=" + existing.getClass().getName());
			return null;
		}
		if (!createNewIfNull) return null;
		SettlementLevelZoneLevelData data = new SettlementLevelZoneLevelData();
		surfaceLevel.addLevelData(managerKey, data);
		if (Logging.logEnabled) Logging.logMessage("[LevelZones] Created level-aware zone data level=" + surfaceLevel.getIdentifier());
		return data;
	}

	public synchronized void setEntry(int settlementUniqueID, ZoneKind kind, LevelIdentifier levelIdentifier, int uniqueID, int zoneID, List<Rectangle> rectangles) {
		entries.removeIf(entry -> entry.settlementUniqueID == settlementUniqueID && entry.kind == kind && entry.levelIdentifier.equals(levelIdentifier) && entry.uniqueID == uniqueID);
		ArrayList<Rectangle> copied = copyRectangles(rectangles);
		entries.add(new Entry(settlementUniqueID, kind, levelIdentifier, uniqueID, zoneID, copied));
	}

	public synchronized void removeEntry(int settlementUniqueID, ZoneKind kind, LevelIdentifier levelIdentifier, int uniqueID) {
		entries.removeIf(entry -> entry.settlementUniqueID == settlementUniqueID && entry.kind == kind && entry.levelIdentifier.equals(levelIdentifier) && entry.uniqueID == uniqueID);
	}

	public synchronized Entry getEntry(int settlementUniqueID, ZoneKind kind, LevelIdentifier levelIdentifier, int uniqueID) {
		for (Entry entry : entries) {
			if (entry.settlementUniqueID == settlementUniqueID && entry.kind == kind && entry.levelIdentifier.equals(levelIdentifier) && entry.uniqueID == uniqueID) return entry.copy();
		}
		return null;
	}

	public synchronized List<Entry> getEntries(int settlementUniqueID, ZoneKind kind, LevelIdentifier levelIdentifier) {
		ArrayList<Entry> result = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.settlementUniqueID == settlementUniqueID && entry.kind == kind && entry.levelIdentifier.equals(levelIdentifier)) result.add(entry.copy());
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
			SaveData entrySave = new SaveData("ZONE");
			entrySave.addInt("settlementUniqueID", entry.settlementUniqueID);
			entrySave.addSafeString("kind", entry.kind.name());
			entrySave.addSafeString("level", entry.levelIdentifier.stringID);
			entrySave.addInt("uniqueID", entry.uniqueID);
			entrySave.addInt("zoneID", entry.zoneID);
			for (Rectangle rectangle : entry.rectangles) {
				SaveData rectSave = new SaveData("RECT");
				rectSave.addInt("x", rectangle.x);
				rectSave.addInt("y", rectangle.y);
				rectSave.addInt("width", rectangle.width);
				rectSave.addInt("height", rectangle.height);
				entrySave.addSaveData(rectSave);
			}
			save.addSaveData(entrySave);
		}
	}

	@Override
	public synchronized void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		entries.clear();
		for (LoadData entrySave : save.getLoadDataByName("ZONE")) {
			int settlementUniqueID = entrySave.getInt("settlementUniqueID", 0, false);
			String kindName = entrySave.getSafeString("kind", null, false);
			String levelName = entrySave.getSafeString("level", null, false);
			int uniqueID = entrySave.getInt("uniqueID", 0, false);
			int zoneID = entrySave.getInt("zoneID", -1, false);
			if (settlementUniqueID == 0 || kindName == null || levelName == null || uniqueID == 0) continue;
			try {
				ZoneKind kind = ZoneKind.valueOf(kindName);
				ArrayList<Rectangle> rectangles = new ArrayList<>();
				for (LoadData rectSave : entrySave.getLoadDataByName("RECT")) {
					int x = rectSave.getInt("x", 0, false);
					int y = rectSave.getInt("y", 0, false);
					int width = rectSave.getInt("width", 0, false);
					int height = rectSave.getInt("height", 0, false);
					if (width > 0 && height > 0) rectangles.add(new Rectangle(x, y, width, height));
				}
				entries.add(new Entry(settlementUniqueID, kind, new LevelIdentifier(levelName), uniqueID, zoneID, rectangles));
			} catch (IllegalArgumentException ignored) {
				if (Logging.logEnabled) Logging.logMessage("[LevelZones] Ignored invalid persisted zone kind=" + kindName + " level=" + levelName + " uniqueID=" + uniqueID);
			}
		}
		if (Logging.logEnabled && !entries.isEmpty()) Logging.logMessage("[LevelZones] Loaded level-aware zone entries count=" + entries.size() + " level=" + level.getIdentifier());
	}

	private static ArrayList<Rectangle> copyRectangles(List<Rectangle> rectangles) {
		ArrayList<Rectangle> result = new ArrayList<>();
		if (rectangles == null) return result;
		for (Rectangle rectangle : rectangles) {
			if (rectangle != null && rectangle.width > 0 && rectangle.height > 0) result.add(new Rectangle(rectangle));
		}
		return result;
	}

	public enum ZoneKind {
		RESTRICT,
		WORK
	}

	public static final class Entry {
		public final int settlementUniqueID;
		public final ZoneKind kind;
		public final LevelIdentifier levelIdentifier;
		public final int uniqueID;
		public final int zoneID;
		public final List<Rectangle> rectangles;

		Entry(int settlementUniqueID, ZoneKind kind, LevelIdentifier levelIdentifier, int uniqueID, int zoneID, List<Rectangle> rectangles) {
			this.settlementUniqueID = settlementUniqueID;
			this.kind = kind;
			this.levelIdentifier = levelIdentifier;
			this.uniqueID = uniqueID;
			this.zoneID = zoneID;
			this.rectangles = Collections.unmodifiableList(copyRectangles(rectangles));
		}

		Entry copy() {
			return new Entry(settlementUniqueID, kind, levelIdentifier, uniqueID, zoneID, rectangles);
		}
	}
}
