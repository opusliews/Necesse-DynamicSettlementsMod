package opusliews.settler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.SettlerRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameMath;
import necesse.engine.util.GameRandom;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.manager.ObjectPlacedListenerEntityComponent;
import necesse.entity.manager.RegionLoadedListenerEntityComponent;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementVisitorSpawner;
import necesse.level.maps.levelData.settlementData.settler.Settler;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import necesse.level.maps.regionSystem.Region;
import opusliews.logging.Logging;
import opusliews.progression.GuideProgressionSystem;
import opusliews.object.JobRequestBulletinObject;
import opusliews.object.JobRequestBulletinObjectEntity;

public class JobRequestLevelData extends LevelData implements
		RegionLoadedListenerEntityComponent,
		ObjectPlacedListenerEntityComponent {

	public static final String managerKey = "opusjobrequests";

	private static final long checkInterval = 1000L;
	private static final long minSpawnDelay = 5L * 60L * 1000L;
	private static final long maxSpawnDelay = 10L * 60L * 1000L;
	private static final long failedSpawnRetryDelay = 30000L;

	private final Set<Long> bulletinTiles = new HashSet<>();
	private final Map<Integer, Long> builderNextSpawnTimes = new HashMap<>();
	private final Map<Integer, Long> carpenterNextSpawnTimes = new HashMap<>();
	private long nextCheckTime;

	public static JobRequestLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) return null;
		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof JobRequestLevelData) return (JobRequestLevelData)existing;
		if (!createNewIfNull) return null;

		JobRequestLevelData data = new JobRequestLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}

	@Override
	public void onLoadingComplete() {
		if (!isServer()) return;
		level.regionManager.forEachLoadedRegions(this::scanRegion);
	}

	@Override
	public void onRegionLoaded(Region region) {
		if (!isServer()) return;
		scanRegion(region);
	}

	@Override
	public void onObjectPlaced(GameObject object, int objectLayerID, int tileX, int tileY, ServerClient client) {
		if (!isServer() || !isBulletinObject(object)) return;
		bulletinTiles.add(GameMath.getUniqueLongKey(tileX, tileY));
		if (client == null) {
			Logging.logMessage("[GuideProgression] Job Request Bulletin placed without ServerClient at " + tileX + "," + tileY);
			return;
		}
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(level)
				.getServerDataAtTile(level.getIdentifier(), tileX, tileY);
		if (settlement != null && settlement.networkData.doesClientHaveAccess(client)) {
			GuideProgressionSystem.recordJobRequestBulletinPlaced(client, settlement);
		}
	}

	@Override
	public void tick() {
		if (!isServer()) return;

		long currentTime = level.getTime();
		if (currentTime < nextCheckTime) return;
		nextCheckTime = currentTime + checkInterval;

		bulletinTiles.removeIf(key -> {
			int tileX = GameMath.getXFromUniqueLongKey(key);
			int tileY = GameMath.getYFromUniqueLongKey(key);
			return !isBulletinAt(tileX, tileY);
		});

		Map<Integer, ServerSettlementData> builderSettlements = new HashMap<>();
		Map<Integer, ServerSettlementData> carpenterSettlements = new HashMap<>();

		for (long key : bulletinTiles) {
			int tileX = GameMath.getXFromUniqueLongKey(key);
			int tileY = GameMath.getYFromUniqueLongKey(key);
			ServerSettlementData settlement = SettlementsWorldData
					.getSettlementsData(level)
					.getServerDataAtTile(level.getIdentifier(), tileX, tileY);
			if (settlement == null) continue;

			ObjectEntity objectEntity = level.entityManager.getObjectEntity(tileX, tileY);
			if (!(objectEntity instanceof JobRequestBulletinObjectEntity)) continue;
			JobRequestBulletinObjectEntity bulletin = (JobRequestBulletinObjectEntity)objectEntity;

			if (bulletin.isBuilderJobsAvailable()) builderSettlements.put(settlement.uniqueID, settlement);
			if (bulletin.isCarpenterJobsAvailable()) carpenterSettlements.put(settlement.uniqueID, settlement);
		}

		builderNextSpawnTimes.keySet().removeIf(id -> !builderSettlements.containsKey(id));
		carpenterNextSpawnTimes.keySet().removeIf(id -> !carpenterSettlements.containsKey(id));

		tickSettlerRequests(builderSettlements, builderNextSpawnTimes, "builder", "Builder", currentTime);
		tickSettlerRequests(carpenterSettlements, carpenterNextSpawnTimes, "carpenter", "Carpenter", currentTime);
	}

	private void tickSettlerRequests(
			Map<Integer, ServerSettlementData> activeSettlements,
			Map<Integer, Long> nextSpawnTimes,
			String settlerStringID,
			String displayName,
			long currentTime
	) {
		for (Map.Entry<Integer, ServerSettlementData> active : activeSettlements.entrySet()) {
			int settlementUniqueID = active.getKey();
			ServerSettlementData settlement = active.getValue();
			long nextSpawnTime = nextSpawnTimes.computeIfAbsent(settlementUniqueID, id -> currentTime + rollSpawnDelay());
			if (currentTime < nextSpawnTime) continue;

			if (spawnVisitor(settlement, settlerStringID, displayName)) {
				nextSpawnTimes.put(settlementUniqueID, currentTime + rollSpawnDelay());
			} else {
				nextSpawnTimes.put(settlementUniqueID, currentTime + failedSpawnRetryDelay);
			}
		}
	}

	private void scanRegion(Region region) {
		for (int tileX = region.tileXOffset; tileX < region.tileXOffset + region.tileWidth; tileX++) {
			for (int tileY = region.tileYOffset; tileY < region.tileYOffset + region.tileHeight; tileY++) {
				if (isBulletinAt(tileX, tileY)) bulletinTiles.add(GameMath.getUniqueLongKey(tileX, tileY));
			}
		}
	}

	private boolean isBulletinAt(int tileX, int tileY) {
		for (int layerID = 0; layerID < ObjectLayerRegistry.getTotalLayers(); layerID++) {
			if (isBulletinObject(level.getObject(layerID, tileX, tileY))) return true;
		}
		return false;
	}

	private static boolean isBulletinObject(GameObject object) {
		if (object == null || object.getID() == 0) return false;
		String id = object.getStringID();
		return JobRequestBulletinObject.stringID.equals(id);
	}

	private boolean spawnVisitor(ServerSettlementData settlement, String settlerStringID, String displayName) {
		Settler settler = SettlerRegistry.getSettler(settlerStringID);
		if (settler == null) {
			Logging.logMessage("Could not spawn " + displayName + " visitor: settler is not registered");
			return false;
		}

		SettlerMob mob = settler.getNewSettlerMob(settlement);
		if (mob == null || !(mob.getMob() instanceof HumanMob)) {
			Logging.logMessage("Could not spawn " + displayName + " visitor: failed to create mob");
			return false;
		}

		mob.setSettlerSeed(GameRandom.globalRandom.nextInt(), true);
		boolean spawned = settlement.spawnVisitor(new SettlementVisitorSpawner(
				ServerSettlementData.visitorRecruitsOdds,
				(HumanMob)mob.getMob()
		));

		if (spawned && Logging.logEnabled) {
			Logging.logMessage("Job Request Bulletin spawned a " + displayName + " visitor for settlement " + settlement.uniqueID);
		}
		return spawned;
	}

	private long rollSpawnDelay() {
		return (long)(GameRandom.globalRandom.nextDouble() * (maxSpawnDelay - minSpawnDelay)) + minSpawnDelay;
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		addTimerSaveData(save, "BUILDER_REQUEST_TIMERS", builderNextSpawnTimes);
		addTimerSaveData(save, "CARPENTER_REQUEST_TIMERS", carpenterNextSpawnTimes);
	}

	private static void addTimerSaveData(SaveData save, String name, Map<Integer, Long> timers) {
		if (timers.isEmpty()) return;
		SaveData timersSave = new SaveData(name);
		for (Map.Entry<Integer, Long> entry : timers.entrySet()) {
			SaveData timerSave = new SaveData("TIMER");
			timerSave.addInt("settlementUniqueID", entry.getKey());
			timerSave.addLong("nextSpawnTime", entry.getValue());
			timersSave.addSaveData(timerSave);
		}
		save.addSaveData(timersSave);
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		builderNextSpawnTimes.clear();
		carpenterNextSpawnTimes.clear();
		readTimerSaveData(save, "BUILDER_REQUEST_TIMERS", builderNextSpawnTimes);
		readTimerSaveData(save, "CARPENTER_REQUEST_TIMERS", carpenterNextSpawnTimes);
	}

	private static void readTimerSaveData(LoadData save, String name, Map<Integer, Long> timers) {
		LoadData timersSave = save.getFirstLoadDataByName(name);
		if (timersSave == null) return;
		for (Object object : timersSave.getLoadData()) {
			LoadData timerLoad = (LoadData)object;
			int settlementUniqueID = timerLoad.getInt("settlementUniqueID");
			long nextSpawnTime = timerLoad.getLong("nextSpawnTime", 0L, false);
			timers.put(settlementUniqueID, nextSpawnTime);
		}
	}
}
