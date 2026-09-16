package opusliews.earlygame;

import java.util.HashSet;
import java.util.Set;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;

public class EarlyGameLevelData extends LevelData {
	public static final String managerKey = "opusearlygamedata";
	private final Set<Long> harvestedSmallRocks = new HashSet<>();

	public static EarlyGameLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) {
			return null;
		}

		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof EarlyGameLevelData) {
			return (EarlyGameLevelData)existing;
		}

		if (!createNewIfNull) {
			return null;
		}

		EarlyGameLevelData data = new EarlyGameLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	public boolean hasHarvestedSmallRock(int tileX, int tileY) {
		return harvestedSmallRocks.contains(getKey(tileX, tileY));
	}

	public void markSmallRockHarvested(int tileX, int tileY) {
		harvestedSmallRocks.add(getKey(tileX, tileY));
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		for (long key : harvestedSmallRocks) {
			SaveData rock = new SaveData("HARVESTED_SMALL_ROCK");
			rock.addInt("tileX", (int)(key >> 32));
			rock.addInt("tileY", (int)key);
			save.addSaveData(rock);
		}
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		harvestedSmallRocks.clear();
		for (LoadData rock : save.getLoadDataByName("HARVESTED_SMALL_ROCK")) {
			harvestedSmallRocks.add(getKey(
					rock.getInt("tileX", 0, false),
					rock.getInt("tileY", 0, false)
			));
		}
	}

	private static long getKey(int tileX, int tileY) {
		return ((long)tileX << 32) | (tileY & 0xffffffffL);
	}
}
