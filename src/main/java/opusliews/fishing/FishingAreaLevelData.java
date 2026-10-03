package opusliews.fishing;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.util.Zoning;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import opusliews.logging.Logging;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FishingAreaLevelData extends LevelData {
	public static final String managerKey = "opusfishingareas";
	public static final int maxAreas = 32;
	private final HashMap<Integer, SettlementState> states = new HashMap<>();

	public static FishingAreaLevelData get(Level surfaceLevel, boolean createNewIfNull) {
		if (surfaceLevel == null) return null;
		LevelData existing = surfaceLevel.getLevelData(managerKey);
		if (existing instanceof FishingAreaLevelData) return (FishingAreaLevelData)existing;
		if (existing != null) {
			if (Logging.logEnabled) Logging.logMessage("[FishingAreas] Level data key collision level=" + surfaceLevel.getIdentifier() + " class=" + existing.getClass().getName());
			return null;
		}
		if (!createNewIfNull) return null;
		FishingAreaLevelData data = new FishingAreaLevelData();
		surfaceLevel.addLevelData(managerKey, data);
		if (Logging.logEnabled) Logging.logMessage("[FishingAreas] Created fishing area level data level=" + surfaceLevel.getIdentifier());
		return data;
	}

	private SettlementState getState(int settlementUniqueID, boolean create) {
		SettlementState state = states.get(settlementUniqueID);
		if (state == null && create) {
			state = new SettlementState();
			states.put(settlementUniqueID, state);
		}
		return state;
	}

	public synchronized FishingArea createArea(int settlementUniqueID, LevelIdentifier levelIdentifier) {
		if (levelIdentifier == null) return null;
		SettlementState state = getState(settlementUniqueID, true);
		if (state.areas.size() >= maxAreas) return null;
		int uniqueID = state.nextAreaID++;
		while (uniqueID == 0 || state.areas.containsKey(uniqueID)) uniqueID = state.nextAreaID++;
		int index = state.areas.values().stream().mapToInt(area -> area.index).max().orElse(-1) + 1;
		FishingArea area = new FishingArea(uniqueID, index, (index * 67) % 360, "Fishing Area " + (index + 1), levelIdentifier, new Zoning());
		state.areas.put(uniqueID, area);
		return area.copy();
	}

	public synchronized boolean deleteArea(int settlementUniqueID, int areaUniqueID) {
		SettlementState state = getState(settlementUniqueID, false);
		if (state == null || state.areas.remove(areaUniqueID) == null) return false;
		state.assignments.entrySet().removeIf(entry -> entry.getValue() == areaUniqueID);
		return true;
	}

	public synchronized boolean renameArea(int settlementUniqueID, int areaUniqueID, String name) {
		FishingArea area = getMutableArea(settlementUniqueID, areaUniqueID);
		if (area == null || name == null || name.trim().isEmpty()) return false;
		area.name = name.trim();
		return true;
	}

	public synchronized boolean recolorArea(int settlementUniqueID, int areaUniqueID, int hue) {
		FishingArea area = getMutableArea(settlementUniqueID, areaUniqueID);
		if (area == null) return false;
		area.colorHue = Math.floorMod(hue, 360);
		return true;
	}

	public synchronized boolean changeArea(int settlementUniqueID, int areaUniqueID, LevelIdentifier editingLevel, Rectangle rectangle, boolean expand) {
		FishingArea area = getMutableArea(settlementUniqueID, areaUniqueID);
		if (area == null || editingLevel == null || !area.levelIdentifier.equals(editingLevel) || rectangle == null || rectangle.isEmpty()) return false;
		if (expand) area.zoning.addRectangle(rectangle);
		else area.zoning.removeRectangle(rectangle);
		return true;
	}

	public synchronized boolean assignArea(int settlementUniqueID, int mobUniqueID, int areaUniqueID) {
		SettlementState state = getState(settlementUniqueID, true);
		if (areaUniqueID == 0) {
			state.assignments.remove(mobUniqueID);
			return true;
		}
		if (!state.areas.containsKey(areaUniqueID)) return false;
		state.assignments.put(mobUniqueID, areaUniqueID);
		return true;
	}

	public synchronized int getAssignedAreaID(int settlementUniqueID, int mobUniqueID) {
		SettlementState state = getState(settlementUniqueID, false);
		return state == null ? 0 : state.assignments.getOrDefault(mobUniqueID, 0);
	}

	public synchronized FishingArea getArea(int settlementUniqueID, int areaUniqueID) {
		FishingArea area = getMutableArea(settlementUniqueID, areaUniqueID);
		return area == null ? null : area.copy();
	}

	private FishingArea getMutableArea(int settlementUniqueID, int areaUniqueID) {
		SettlementState state = getState(settlementUniqueID, false);
		return state == null ? null : state.areas.get(areaUniqueID);
	}

	public synchronized void clearSettlement(int settlementUniqueID) {
		states.remove(settlementUniqueID);
	}

	public synchronized Snapshot snapshot(int settlementUniqueID) {
		SettlementState state = getState(settlementUniqueID, false);
		ArrayList<FishingArea> areas = new ArrayList<>();
		HashMap<Integer, Integer> assignments = new HashMap<>();
		if (state != null) {
			for (FishingArea area : state.areas.values()) areas.add(area.copy());
			assignments.putAll(state.assignments);
		}
		areas.sort(Comparator.comparingInt(area -> area.index));
		return new Snapshot(areas, assignments);
	}

	@Override
	public synchronized void addSaveData(SaveData save) {
		super.addSaveData(save);
		for (Map.Entry<Integer, SettlementState> stateEntry : states.entrySet()) {
			SaveData settlementSave = new SaveData("SETTLEMENT");
			settlementSave.addInt("settlementUniqueID", stateEntry.getKey());
			settlementSave.addInt("nextAreaID", stateEntry.getValue().nextAreaID);
			for (FishingArea area : stateEntry.getValue().areas.values()) {
				SaveData areaSave = new SaveData("AREA");
				areaSave.addInt("uniqueID", area.uniqueID);
				areaSave.addInt("index", area.index);
				areaSave.addInt("colorHue", area.colorHue);
				areaSave.addSafeString("name", area.name);
				areaSave.addSafeString("level", area.levelIdentifier.stringID);
				area.zoning.addZoneSaveData("zone", areaSave);
				settlementSave.addSaveData(areaSave);
			}
			for (Map.Entry<Integer, Integer> assignment : stateEntry.getValue().assignments.entrySet()) {
				SaveData assignmentSave = new SaveData("ASSIGNMENT");
				assignmentSave.addInt("mobUniqueID", assignment.getKey());
				assignmentSave.addInt("areaUniqueID", assignment.getValue());
				settlementSave.addSaveData(assignmentSave);
			}
			save.addSaveData(settlementSave);
		}
	}

	@Override
	public synchronized void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		states.clear();
		for (LoadData settlementSave : save.getLoadDataByName("SETTLEMENT")) {
			int settlementUniqueID = settlementSave.getInt("settlementUniqueID", 0, false);
			if (settlementUniqueID == 0) continue;
			SettlementState state = new SettlementState();
			state.nextAreaID = Math.max(1, settlementSave.getInt("nextAreaID", 1, false));
			for (LoadData areaSave : settlementSave.getLoadDataByName("AREA")) {
				try {
					int uniqueID = areaSave.getInt("uniqueID", 0, false);
					if (uniqueID == 0) continue;
					int index = areaSave.getInt("index", state.areas.size(), false);
					int colorHue = Math.floorMod(areaSave.getInt("colorHue", 0, false), 360);
					String name = areaSave.getSafeString("name", "Fishing Area", false);
					String level = areaSave.getSafeString("level", null, false);
					if (level == null) continue;
					Zoning zoning = new Zoning();
					zoning.applyZoneSaveData("zone", areaSave, 0, 0);
					state.areas.put(uniqueID, new FishingArea(uniqueID, index, colorHue, name, new LevelIdentifier(level), zoning));
					state.nextAreaID = Math.max(state.nextAreaID, uniqueID + 1);
				} catch (Exception error) {
					if (Logging.logEnabled) Logging.logMessage("[FishingAreas] Ignored invalid saved fishing area settlement=" + settlementUniqueID + " error=" + error.getMessage());
				}
			}
			for (LoadData assignmentSave : settlementSave.getLoadDataByName("ASSIGNMENT")) {
				if (!assignmentSave.hasLoadDataByName("mobUniqueID")) continue;
				int mobUniqueID = assignmentSave.getInt("mobUniqueID", 0, false);
				int areaUniqueID = assignmentSave.getInt("areaUniqueID", 0, false);
				if (areaUniqueID != 0 && state.areas.containsKey(areaUniqueID)) state.assignments.put(mobUniqueID, areaUniqueID);
			}
			states.put(settlementUniqueID, state);
		}
		if (Logging.logEnabled && !states.isEmpty()) Logging.logMessage("[FishingAreas] Loaded fishing area settlement states count=" + states.size() + " level=" + level.getIdentifier());
	}

	private static final class SettlementState {
		private int nextAreaID = 1;
		private final LinkedHashMap<Integer, FishingArea> areas = new LinkedHashMap<>();
		private final HashMap<Integer, Integer> assignments = new HashMap<>();
	}

	public static final class FishingArea {
		public final int uniqueID;
		public final int index;
		public int colorHue;
		public String name;
		public final LevelIdentifier levelIdentifier;
		public final Zoning zoning;

		public FishingArea(int uniqueID, int index, int colorHue, String name, LevelIdentifier levelIdentifier, Zoning zoning) {
			this.uniqueID = uniqueID;
			this.index = index;
			this.colorHue = colorHue;
			this.name = name;
			this.levelIdentifier = levelIdentifier;
			this.zoning = zoning == null ? new Zoning() : zoning;
		}

		public FishingArea copy() {
			Zoning copy = new Zoning();
			for (Object value : zoning.getTileRectangles()) copy.addRectangle(new Rectangle((Rectangle)value));
			return new FishingArea(uniqueID, index, colorHue, name, levelIdentifier, copy);
		}
	}

	public static final class Snapshot {
		public final List<FishingArea> areas;
		public final Map<Integer, Integer> assignments;

		public Snapshot(List<FishingArea> areas, Map<Integer, Integer> assignments) {
			this.areas = areas;
			this.assignments = assignments;
		}
	}
}
