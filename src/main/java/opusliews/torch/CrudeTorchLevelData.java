package opusliews.torch;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import opusliews.object.CrudeTorchObject;
import opusliews.object.CrudeWallTorchObject;

public class CrudeTorchLevelData extends LevelData {
	public static final String managerKey = "opuscrudetorchdata";
	private static final long dayDurationMultiplier = 1000L;

	private final Map<Long, TrackedTorch> torches = new HashMap<>();

	public static CrudeTorchLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) return null;

		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof CrudeTorchLevelData) return (CrudeTorchLevelData)existing;
		if (!createNewIfNull) return null;

		CrudeTorchLevelData data = new CrudeTorchLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	public static long getDayDurationMillis(Level level) {
		return (long)level.getWorldEntity().getDayTimeMax() * dayDurationMultiplier;
	}

	public void registerTorch(int layerID, int tileX, int tileY, long expireWorldTime, long duration) {
		torches.put(getKey(layerID, tileX, tileY), new TrackedTorch(layerID, tileX, tileY, expireWorldTime, duration));
	}

	public TrackedTorch getTrackedTorch(int layerID, int tileX, int tileY) {
		return torches.get(getKey(layerID, tileX, tileY));
	}

	public void removeTrackedTorch(int layerID, int tileX, int tileY) {
		torches.remove(getKey(layerID, tileX, tileY));
	}

	@Override
	public void tick() {
		if (!isServer() || torches.isEmpty()) return;

		long currentWorldTime = getWorldEntity().getWorldTime();
		Iterator<Map.Entry<Long, TrackedTorch>> iterator = torches.entrySet().iterator();
		while (iterator.hasNext()) {
			TrackedTorch tracked = iterator.next().getValue();
			int currentObjectID = level.getObjectID(tracked.layerID, tracked.tileX, tracked.tileY);
			if (!isTrackedCrudeTorch(currentObjectID)) {
				iterator.remove();
				continue;
			}

			if (currentWorldTime >= tracked.expireWorldTime) {
				removeTorch(tracked.layerID, tracked.tileX, tracked.tileY);
				iterator.remove();
			}
		}
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		for (TrackedTorch tracked : torches.values()) {
			SaveData torchSave = new SaveData("CRUDE_TORCH");
			torchSave.addInt("layerID", tracked.layerID);
			torchSave.addInt("tileX", tracked.tileX);
			torchSave.addInt("tileY", tracked.tileY);
			torchSave.addLong("expireWorldTime", tracked.expireWorldTime);
			torchSave.addLong("duration", tracked.duration);
			save.addSaveData(torchSave);
		}
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		torches.clear();
		for (LoadData torchSave : save.getLoadDataByName("CRUDE_TORCH")) {
			int layerID = torchSave.getInt("layerID", 0, false);
			int tileX = torchSave.getInt("tileX", 0, false);
			int tileY = torchSave.getInt("tileY", 0, false);
			long expireWorldTime = torchSave.getLong("expireWorldTime", 0L, false);
			long duration = torchSave.getLong("duration", getDayDurationMillis(level), false);
			torches.put(getKey(layerID, tileX, tileY), new TrackedTorch(layerID, tileX, tileY, expireWorldTime, duration));
		}
	}

	private void removeTorch(int layerID, int tileX, int tileY) {
		if (layerID == 0) {
			ObjectEntity objectEntity = level.entityManager.getObjectEntity(tileX, tileY);
			level.setObject(tileX, tileY, 0, 0);
			level.objectLayer.setIsPlayerPlaced(layerID, tileX, tileY, false);
			if (objectEntity != null) objectEntity.remove();
		} else {
			level.objectLayer.setObject(layerID, tileX, tileY, 0);
			level.objectLayer.setObjectRotation(layerID, tileX, tileY, 0);
			level.objectLayer.setIsPlayerPlaced(layerID, tileX, tileY, false);
		}

		level.lightManager.updateStaticLight(tileX, tileY, tileX, tileY, true);
		if (level.getServer() != null) {
			level.getServer().network.sendToClientsWithTile(
					new PacketChangeObject(level, layerID, tileX, tileY, 0, 0, false), level, tileX, tileY
			);
		}
	}

	private static boolean isTrackedCrudeTorch(int objectID) {
		return objectID == ObjectRegistry.getObjectID(CrudeTorchObject.stringID)
				|| objectID == ObjectRegistry.getObjectID(CrudeWallTorchObject.stringID);
	}

	private static long getKey(int layerID, int tileX, int tileY) {
		return ((long)(layerID & 0x3) << 62) | ((long)tileX & 0x7fffffffL) << 31 | ((long)tileY & 0x7fffffffL);
	}

	public static class TrackedTorch {
		public final int layerID;
		public final int tileX;
		public final int tileY;
		public final long expireWorldTime;
		public final long duration;

		private TrackedTorch(int layerID, int tileX, int tileY, long expireWorldTime, long duration) {
			this.layerID = layerID;
			this.tileX = tileX;
			this.tileY = tileY;
			this.expireWorldTime = expireWorldTime;
			this.duration = duration;
		}
	}
}
