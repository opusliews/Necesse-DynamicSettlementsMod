package opusliews.multilevelsettlement;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import opusliews.logging.Logging;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

public class SettlementCaveBedLevelData extends LevelData {
	public static final String managerKey = "opuscavesettlementbeds";
	private final HashMap<Integer, Point> assignedBeds = new HashMap<>();

	public static SettlementCaveBedLevelData get(Level surfaceLevel, boolean createNewIfNull) {
		if (surfaceLevel == null) return null;
		LevelData existing = surfaceLevel.getLevelData(managerKey);
		if (existing instanceof SettlementCaveBedLevelData) return (SettlementCaveBedLevelData)existing;
		if (existing != null) {
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Level data key collision level=" + surfaceLevel.getIdentifier() + " class=" + existing.getClass().getName());
			return null;
		}
		if (!createNewIfNull) return null;
		SettlementCaveBedLevelData data = new SettlementCaveBedLevelData();
		surfaceLevel.addLevelData(managerKey, data);
		if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Created cave bed level data level=" + surfaceLevel.getIdentifier());
		return data;
	}

	public synchronized void setAssignedBed(int mobUniqueID, int tileX, int tileY) {
		assignedBeds.put(mobUniqueID, new Point(tileX, tileY));
	}

	public synchronized Point getAssignedBed(int mobUniqueID) {
		Point point = assignedBeds.get(mobUniqueID);
		return point == null ? null : new Point(point);
	}

	public synchronized void clearAssignedBed(int mobUniqueID) {
		assignedBeds.remove(mobUniqueID);
	}

	public synchronized Map<Integer, Point> getAssignedBedsSnapshot() {
		HashMap<Integer, Point> copy = new HashMap<>();
		for (Map.Entry<Integer, Point> entry : assignedBeds.entrySet()) copy.put(entry.getKey(), new Point(entry.getValue()));
		return copy;
	}

	@Override
	public synchronized void addSaveData(SaveData save) {
		super.addSaveData(save);
		for (Map.Entry<Integer, Point> entry : assignedBeds.entrySet()) {
			SaveData bedSave = new SaveData("CAVE_BED");
			bedSave.addInt("mobUniqueID", entry.getKey());
			bedSave.addInt("tileX", entry.getValue().x);
			bedSave.addInt("tileY", entry.getValue().y);
			save.addSaveData(bedSave);
		}
	}

	@Override
	public synchronized void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		assignedBeds.clear();
		for (LoadData bedSave : save.getLoadDataByName("CAVE_BED")) {
			int mobUniqueID = bedSave.getInt("mobUniqueID", -1, false);
			int tileX = bedSave.getInt("tileX", 0, false);
			int tileY = bedSave.getInt("tileY", 0, false);
			if (mobUniqueID < 0) {
				if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Ignored saved cave bed with invalid mob ID tile=" + tileX + "," + tileY);
				continue;
			}
			assignedBeds.put(mobUniqueID, new Point(tileX, tileY));
		}
		if (Logging.logEnabled && !assignedBeds.isEmpty()) Logging.logMessage("[CaveBeds] Loaded cave bed assignments count=" + assignedBeds.size() + " level=" + level.getIdentifier());
	}
}
