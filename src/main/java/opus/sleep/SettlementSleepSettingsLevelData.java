package opus.sleep;

import java.util.HashMap;
import java.util.Map;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;

public class SettlementSleepSettingsLevelData extends LevelData {
	public static final String managerKey = "opussettlementsleepsettings";

	private final Map<Integer, SettlementSleepSettings> settings = new HashMap<>();

	public static SettlementSleepSettingsLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) {
			return null;
		}

		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof SettlementSleepSettingsLevelData) {
			return (SettlementSleepSettingsLevelData)existing;
		}

		if (!createNewIfNull) {
			return null;
		}

		SettlementSleepSettingsLevelData data = new SettlementSleepSettingsLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	public SettlementSleepSettings getSettings(int settlementUniqueID) {
		return settings.getOrDefault(settlementUniqueID, SettlementSleepSettings.defaults);
	}

	public void setSettings(int settlementUniqueID, SettlementSleepSettings value) {
		if (settlementUniqueID == 0 || value == null) {
			return;
		}

		settings.put(settlementUniqueID, value);
	}

	@Override
	public void tick() {
		if (isServer()) {
			SleepWarningSystem.tick(level);
		}
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		if (settings.isEmpty()) {
			return;
		}

		SaveData root = new SaveData("SETTLEMENT_SLEEP_SETTINGS");
		for (Map.Entry<Integer, SettlementSleepSettings> entry : settings.entrySet()) {
			SaveData item = new SaveData("SETTING");
			item.addInt("settlementUniqueID", entry.getKey());
			item.addBoolean("wakeOnRaid", entry.getValue().wakeOnRaid);
			item.addBoolean("wakeOnBarrierAttack", entry.getValue().wakeOnBarrierAttack);
			item.addBoolean("wakeOnBarrierBreach", entry.getValue().wakeOnBarrierBreach);
			root.addSaveData(item);
		}
		save.addSaveData(root);
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		settings.clear();

		LoadData root = save.getFirstLoadDataByName("SETTLEMENT_SLEEP_SETTINGS");
		if (root == null) {
			return;
		}

		for (LoadData item : root.getLoadData()) {
			int settlementUniqueID = item.getInt("settlementUniqueID", 0, false);
			if (settlementUniqueID == 0) {
				continue;
			}

			settings.put(settlementUniqueID, new SettlementSleepSettings(
					item.getBoolean("wakeOnRaid", true, false),
					item.getBoolean("wakeOnBarrierAttack", true, false),
					item.getBoolean("wakeOnBarrierBreach", true, false)
			));
		}
	}
}
