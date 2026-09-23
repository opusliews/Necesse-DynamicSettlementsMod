package opusliews.clayfiring;

import java.util.LinkedHashMap;
import java.util.Map;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;

public class ClayFiringSettingsLevelData extends LevelData {
	public static final String managerKey = "opusclayfiringsettings";

	private final LinkedHashMap<String, Integer> produceUntilUnitsStocked = new LinkedHashMap<>();
	private boolean repeatForever;

	public static ClayFiringSettingsLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) return null;
		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof ClayFiringSettingsLevelData) return (ClayFiringSettingsLevelData)existing;
		if (!createNewIfNull) return null;
		ClayFiringSettingsLevelData data = new ClayFiringSettingsLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	public boolean isRepeatForever() {
		return repeatForever;
	}

	public int getProduceUntilUnitsStocked(String firedItemStringID) {
		return Math.max(0, produceUntilUnitsStocked.getOrDefault(firedItemStringID, 0));
	}

	public Map<String, Integer> getProductionTargets() {
		return new LinkedHashMap<>(produceUntilUnitsStocked);
	}

	public void setProductionSettings(Map<String, Integer> targets, boolean repeatForever) {
		produceUntilUnitsStocked.clear();
		if (targets != null) {
			for (Map.Entry<String, Integer> entry : targets.entrySet()) {
				if (entry.getKey() == null || entry.getKey().isEmpty()) continue;
				produceUntilUnitsStocked.put(entry.getKey(), Math.max(0, entry.getValue() == null ? 0 : entry.getValue()));
			}
		}
		this.repeatForever = repeatForever;
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		save.addBoolean("repeatForever", repeatForever);
		for (Map.Entry<String, Integer> entry : produceUntilUnitsStocked.entrySet()) {
			SaveData target = new SaveData("TARGET");
			target.addSafeString("itemStringID", entry.getKey());
			target.addInt("amount", Math.max(0, entry.getValue()));
			save.addSaveData(target);
		}
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		produceUntilUnitsStocked.clear();
		repeatForever = save.getBoolean("repeatForever", false, false);
		for (LoadData target : save.getLoadDataByName("TARGET")) {
			String itemStringID = target.getSafeString("itemStringID", "", false);
			if (itemStringID.isEmpty()) continue;
			produceUntilUnitsStocked.put(itemStringID, Math.max(0, target.getInt("amount", 0, false)));
		}
	}
}
