package opusliews.tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;

public class CharcoalPitLevelData extends LevelData {
	public static final String managerKey = "opuscharcoalpitdata";

	private final Map<Long, List<StoredLog>> pitLogs = new HashMap<>();

	public static CharcoalPitLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) {
			return null;
		}

		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof CharcoalPitLevelData) {
			return (CharcoalPitLevelData)existing;
		}

		if (!createNewIfNull) {
			return null;
		}

		CharcoalPitLevelData data = new CharcoalPitLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	public void setLogs(int tileX, int tileY, List<StoredLog> logs) {
		pitLogs.put(getKey(tileX, tileY), copyLogs(logs));
	}

	public List<StoredLog> removeLogs(int tileX, int tileY) {
		List<StoredLog> logs = pitLogs.remove(getKey(tileX, tileY));
		return logs == null ? new ArrayList<>() : logs;
	}

	public List<StoredLog> getLogs(int tileX, int tileY) {
		List<StoredLog> logs = pitLogs.get(getKey(tileX, tileY));
		return logs == null ? new ArrayList<>() : copyLogs(logs);
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);

		for (Map.Entry<Long, List<StoredLog>> entry : pitLogs.entrySet()) {
			int tileX = (int)(entry.getKey() >> 32);
			int tileY = (int)(long)entry.getKey();
			SaveData pit = new SaveData("CHARCOAL_PIT");
			pit.addInt("tileX", tileX);
			pit.addInt("tileY", tileY);

			for (StoredLog log : entry.getValue()) {
				SaveData logData = new SaveData("LOG");
				logData.addSafeString("itemStringID", log.itemStringID);
				logData.addInt("amount", log.amount);
				pit.addSaveData(logData);
			}

			save.addSaveData(pit);
		}
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		pitLogs.clear();

		for (LoadData pit : save.getLoadDataByName("CHARCOAL_PIT")) {
			int tileX = pit.getInt("tileX", 0, false);
			int tileY = pit.getInt("tileY", 0, false);
			List<StoredLog> logs = new ArrayList<>();

			for (LoadData logData : pit.getLoadDataByName("LOG")) {
				String itemStringID = logData.getSafeString("itemStringID", "", false);
				int amount = logData.getInt("amount", 0, false);

				if (!itemStringID.isEmpty() && amount > 0) {
					logs.add(new StoredLog(itemStringID, amount));
				}
			}

			if (!logs.isEmpty()) {
				pitLogs.put(getKey(tileX, tileY), logs);
			}
		}
	}

	private static long getKey(int tileX, int tileY) {
		return ((long)tileX << 32) | (tileY & 0xffffffffL);
	}

	private static List<StoredLog> copyLogs(List<StoredLog> logs) {
		List<StoredLog> copy = new ArrayList<>();
		if (logs != null) {
			for (StoredLog log : logs) {
				copy.add(new StoredLog(log.itemStringID, log.amount));
			}
		}
		return copy;
	}

	public static class StoredLog {
		public final String itemStringID;
		public final int amount;

		public StoredLog(String itemStringID, int amount) {
			this.itemStringID = itemStringID;
			this.amount = amount;
		}
	}
}
