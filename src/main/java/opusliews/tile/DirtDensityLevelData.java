package opusliews.tile;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import necesse.engine.registries.TileRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import opusliews.logging.Logging;

public class DirtDensityLevelData extends LevelData {
	public static final String managerKey = "opusdirtdensitydata";
	private static final String saveEntry = "THIN_DIRT";
	private static final long millisPerDayTimeUnit = 1000L;
	private static final int recoveryCycles = 2;

	private final Map<Long, Long> recoverAtWorldTime = new HashMap<>();

	public static DirtDensityLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) return null;

		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof DirtDensityLevelData) return (DirtDensityLevelData)existing;
		if (!createNewIfNull) return null;

		DirtDensityLevelData data = new DirtDensityLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	public static long getRecoveryDuration(Level level) {
		if (level == null || level.getWorldEntity() == null) return 0L;
		return (long)level.getWorldEntity().getDayTimeMax() * millisPerDayTimeUnit * recoveryCycles;
	}

	public static void markThin(Level level, int tileX, int tileY) {
		if (level == null || !level.isServer()) return;
		DirtDensityLevelData data = get(level, true);
		long now = level.getWorldEntity().getWorldTime();
		long recoverAt = now + getRecoveryDuration(level);
		data.recoverAtWorldTime.put(getKey(tileX, tileY), recoverAt);
		if (Logging.logEnabled) Logging.logMessage("[DirtDensity] Density changed 2->1 level=" + level.getIdentifier()
				+ " tile=" + tileX + "," + tileY + " recoverAt=" + recoverAt);
	}

	public static void clear(Level level, int tileX, int tileY) {
		if (level == null) return;
		DirtDensityLevelData data = get(level, false);
		if (data != null) data.recoverAtWorldTime.remove(getKey(tileX, tileY));
	}

	public static int prepareTileReplacement(Level level, int tileX, int tileY, int requestedTileID) {
		if (level == null || !level.isServer() || !level.isTileWithinBounds(tileX, tileY)) return requestedTileID;

		DirtDensityLevelData data = get(level, false);
		if (data == null) return requestedTileID;

		long key = getKey(tileX, tileY);
		Long recoverAt = data.recoverAtWorldTime.get(key);
		if (recoverAt == null) return requestedTileID;

		long now = level.getWorldEntity() == null ? 0L : level.getWorldEntity().getWorldTime();
		if (now >= recoverAt) {
			data.recoverAtWorldTime.remove(key);
			if (Logging.logEnabled) Logging.logMessage("[DirtDensity] Covered thin dirt finished recovering before tile replacement level=" + level.getIdentifier()
					+ " tile=" + tileX + "," + tileY + " requestedTileID=" + requestedTileID);
			return requestedTileID;
		}

		int thinDirtID = TileRegistry.getTileID(ThinDirtTile.stringID);
		int currentTileID = level.getTileID(tileX, tileY);

		if (requestedTileID == TileRegistry.dirtID) {
			if (currentTileID != thinDirtID && Logging.logEnabled) Logging.logMessage("[DirtDensity] Revealing still-thin dirt from beneath covering tile level=" + level.getIdentifier()
					+ " tile=" + tileX + "," + tileY + " coveredTileID=" + currentTileID + " recoverAt=" + recoverAt);
			return thinDirtID;
		}

		if (requestedTileID == thinDirtID || isCoveringTile(requestedTileID)) return requestedTileID;

		data.recoverAtWorldTime.remove(key);
		if (Logging.logEnabled) Logging.logMessage("[DirtDensity] Cleared underlying thin-dirt state because replacement is not a dirt-covering tile level=" + level.getIdentifier()
				+ " tile=" + tileX + "," + tileY + " oldTileID=" + currentTileID + " requestedTileID=" + requestedTileID);
		return requestedTileID;
	}

	private static boolean isCoveringTile(int tileID) {
		GameTile tile = TileRegistry.getTile(tileID);
		if (tile == null || tile.isLiquid || tile instanceof ShallowHoleTile) return false;
		return tile.getDestroyedTile() == TileRegistry.dirtID;
	}

	public static int getDensity(Level level, int tileX, int tileY) {
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) return 2;
		if (level.getTileID(tileX, tileY) == TileRegistry.getTileID(ThinDirtTile.stringID)) return 1;

		if (level.isServer()) {
			DirtDensityLevelData data = get(level, false);
			if (data != null) {
				Long recoverAt = data.recoverAtWorldTime.get(getKey(tileX, tileY));
				long now = level.getWorldEntity() == null ? 0L : level.getWorldEntity().getWorldTime();
				if (recoverAt != null && now < recoverAt) return 1;
			}
		}

		return 2;
	}

	@Override
	public void tick() {
		if (!isServer() || recoverAtWorldTime.isEmpty()) return;

		long now = getWorldEntity().getWorldTime();
		Iterator<Map.Entry<Long, Long>> iterator = recoverAtWorldTime.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Long, Long> entry = iterator.next();
			if (now < entry.getValue()) continue;

			int tileX = getTileX(entry.getKey());
			int tileY = getTileY(entry.getKey());
			if (!level.regionManager.isTileLoaded(tileX, tileY)) continue;

			int currentTileID = level.getTileID(tileX, tileY);
			iterator.remove();
			if (currentTileID == TileRegistry.getTileID(ThinDirtTile.stringID)) {
				level.setTile(tileX, tileY, TileRegistry.dirtID);
				level.sendTileUpdatePacket(tileX, tileY);
				level.getLevelTile(tileX, tileY).checkAround();
				level.getLevelObject(tileX, tileY).checkAround();
				if (Logging.logEnabled) Logging.logMessage("[DirtDensity] Density recovered 1->2 level=" + level.getIdentifier()
						+ " tile=" + tileX + "," + tileY + " worldTime=" + now);
			} else if (Logging.logEnabled) {
				Logging.logMessage("[DirtDensity] Covered density-1 dirt recovered to density 2 under tile level=" + level.getIdentifier()
						+ " tile=" + tileX + "," + tileY + " coveringTileID=" + currentTileID + " worldTime=" + now);
			}
		}
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		for (Map.Entry<Long, Long> entry : recoverAtWorldTime.entrySet()) {
			SaveData thin = new SaveData(saveEntry);
			thin.addInt("tileX", getTileX(entry.getKey()));
			thin.addInt("tileY", getTileY(entry.getKey()));
			thin.addLong("recoverAtWorldTime", entry.getValue());
			save.addSaveData(thin);
		}
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		recoverAtWorldTime.clear();
		for (LoadData thin : save.getLoadDataByName(saveEntry)) {
			int tileX = thin.getInt("tileX", 0, false);
			int tileY = thin.getInt("tileY", 0, false);
			long recoverAt = thin.getLong("recoverAtWorldTime", 0L, false);
			recoverAtWorldTime.put(getKey(tileX, tileY), recoverAt);
		}
	}

	private static long getKey(int tileX, int tileY) {
		return ((long)tileX << 32) | (tileY & 0xffffffffL);
	}

	private static int getTileX(long key) {
		return (int)(key >> 32);
	}

	private static int getTileY(long key) {
		return (int)key;
	}
}
