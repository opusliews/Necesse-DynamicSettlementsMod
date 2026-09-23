package opusliews.clayfiring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.pickup.ItemPickupEntity;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import opusliews.item.FireableMatItem;
import opusliews.jobs.ClayFiringCleanupLevelJob;
import opusliews.logging.Logging;
import opusliews.tile.CharcoalPitLevelData;
import opusliews.tile.CharcoalPitLevelData.StoredFiringItem;

public class ClayFiringAutomationLevelData extends LevelData {
	public static final String managerKey = "opusclayfiringautomation";

	private final Map<Long, Integer> activeProductionZones = new HashMap<>();
	private final Map<Long, Integer> automatedBurnZones = new HashMap<>();
	private final Map<Long, PendingBatch> pendingBatches = new HashMap<>();
	private int productionRoundRobinCursor;

	public static ClayFiringAutomationLevelData get(Level level, boolean createNewIfNull) {
		if (level == null) return null;
		LevelData existing = level.getLevelData(managerKey);
		if (existing instanceof ClayFiringAutomationLevelData) return (ClayFiringAutomationLevelData)existing;
		if (!createNewIfNull) return null;
		ClayFiringAutomationLevelData data = new ClayFiringAutomationLevelData();
		level.addLevelData(managerKey, data);
		return data;
	}


	public int getProductionRoundRobinCursor(int catalogSize) {
		if (catalogSize <= 0) return 0;
		return Math.floorMod(productionRoundRobinCursor, catalogSize);
	}

	public void setProductionRoundRobinCursor(int cursor, int catalogSize) {
		productionRoundRobinCursor = catalogSize <= 0 ? 0 : Math.floorMod(cursor, catalogSize);
	}

	public boolean claimActiveProduction(int tileX, int tileY, int zoneUniqueID) {
		long key = getKey(tileX, tileY);
		Integer existing = activeProductionZones.get(key);
		if (existing != null) return existing == zoneUniqueID;
		if (!activeProductionZones.isEmpty()) return false;
		activeProductionZones.put(key, zoneUniqueID);
		return true;
	}

	public void releaseActiveProduction(int tileX, int tileY) {
		activeProductionZones.remove(getKey(tileX, tileY));
	}

	public boolean hasActiveProduction() {
		return !activeProductionZones.isEmpty();
	}

	public void markAutomatedBurn(int tileX, int tileY, int zoneUniqueID) {
		automatedBurnZones.put(getKey(tileX, tileY), zoneUniqueID);
	}

	public void clearAutomatedBurn(int tileX, int tileY) {
		automatedBurnZones.remove(getKey(tileX, tileY));
	}

	public boolean handleCompletedBurn(int tileX, int tileY, List<ItemPickupEntity> firedPickups) {
		long key = getKey(tileX, tileY);
		Integer zoneUniqueID = automatedBurnZones.remove(key);
		if (zoneUniqueID == null) return false;

		ArrayList<Integer> pickupUniqueIDs = new ArrayList<>();
		LinkedHashMap<String, Integer> expectedAmounts = new LinkedHashMap<>();
		if (firedPickups != null) {
			for (ItemPickupEntity pickup : firedPickups) {
				if (pickup == null || pickup.item == null || pickup.item.getAmount() <= 0) continue;
				pickupUniqueIDs.add(pickup.getUniqueID());
				String itemStringID = pickup.item.item.getStringID();
				expectedAmounts.put(itemStringID, expectedAmounts.getOrDefault(itemStringID, 0) + pickup.item.getAmount());
			}
		}

		PendingBatch pending = new PendingBatch(zoneUniqueID, pickupUniqueIDs, expectedAmounts);
		pendingBatches.put(key, pending);
		level.jobsLayer.addJob(new ClayFiringCleanupLevelJob(tileX, tileY, zoneUniqueID));
		Logging.logMessage("[ClayFiring] Burn completed normally at " + tileX + "," + tileY
				+ "; spawned " + pickupUniqueIDs.size() + " world pickup(s) and queued cleanup");
		return true;
	}

	public int getPotentialFiredAmount(String firedItemStringID) {
		if (firedItemStringID == null || firedItemStringID.isEmpty()) return 0;
		int total = 0;

		for (PendingBatch pending : pendingBatches.values()) {
			total += pending.expectedAmounts.getOrDefault(firedItemStringID, 0);
		}

		CharcoalPitLevelData pitData = CharcoalPitLevelData.get(level, false);
		if (pitData != null) {
			for (Long key : automatedBurnZones.keySet()) {
				int tileX = (int)(key >> 32);
				int tileY = (int)(long)key;
				for (StoredFiringItem stored : pitData.getFiringItems(tileX, tileY)) {
					Item item = ItemRegistry.getItem(stored.itemStringID);
					if (!(item instanceof FireableMatItem)) continue;
					String fired = ((FireableMatItem)item).getFiredItemStringID();
					if (firedItemStringID.equals(fired)) total++;
				}
			}
		}

		return total;
	}

	public PendingBatch getPendingBatch(int tileX, int tileY) {
		return pendingBatches.get(getKey(tileX, tileY));
	}

	public PendingBatch removePendingBatch(int tileX, int tileY) {
		return pendingBatches.remove(getKey(tileX, tileY));
	}

	public List<ItemPickupEntity> getPendingPickups(int tileX, int tileY) {
		ArrayList<ItemPickupEntity> result = new ArrayList<>();
		PendingBatch pending = getPendingBatch(tileX, tileY);
		if (pending == null || level == null) return result;
		for (Integer uniqueID : pending.pickupUniqueIDs) {
			Object value = level.entityManager.pickups.get(uniqueID, false);
			if (!(value instanceof ItemPickupEntity)) continue;
			ItemPickupEntity pickup = (ItemPickupEntity)value;
			if (!pickup.removed() && pickup.item != null && pickup.item.getAmount() > 0) result.add(pickup);
		}
		return result;
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		save.addInt("productionRoundRobinCursor", productionRoundRobinCursor);
		for (Map.Entry<Long, Integer> entry : automatedBurnZones.entrySet()) {
			SaveData burn = new SaveData("ACTIVE_BURN");
			burn.addInt("tileX", (int)(entry.getKey() >> 32));
			burn.addInt("tileY", (int)(long)entry.getKey());
			burn.addInt("zoneUniqueID", entry.getValue());
			save.addSaveData(burn);
		}
		for (Map.Entry<Long, PendingBatch> entry : pendingBatches.entrySet()) {
			SaveData pending = new SaveData("PENDING_BATCH");
			pending.addInt("tileX", (int)(entry.getKey() >> 32));
			pending.addInt("tileY", (int)(long)entry.getKey());
			pending.addInt("zoneUniqueID", entry.getValue().zoneUniqueID);
			for (Integer uniqueID : entry.getValue().pickupUniqueIDs) {
				SaveData pickup = new SaveData("PICKUP");
				pickup.addInt("uniqueID", uniqueID);
				pending.addSaveData(pickup);
			}
			for (Map.Entry<String, Integer> expected : entry.getValue().expectedAmounts.entrySet()) {
				SaveData item = new SaveData("EXPECTED_ITEM");
				item.addSafeString("itemStringID", expected.getKey());
				item.addInt("amount", expected.getValue());
				pending.addSaveData(item);
			}
			save.addSaveData(pending);
		}
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		activeProductionZones.clear();
		automatedBurnZones.clear();
		pendingBatches.clear();
		productionRoundRobinCursor = Math.max(0, save.getInt("productionRoundRobinCursor", 0, false));
		for (LoadData burn : save.getLoadDataByName("ACTIVE_BURN")) {
			int tileX = burn.getInt("tileX", 0, false);
			int tileY = burn.getInt("tileY", 0, false);
			automatedBurnZones.put(getKey(tileX, tileY), burn.getInt("zoneUniqueID", 0, false));
		}
		for (LoadData pending : save.getLoadDataByName("PENDING_BATCH")) {
			int tileX = pending.getInt("tileX", 0, false);
			int tileY = pending.getInt("tileY", 0, false);
			int zoneUniqueID = pending.getInt("zoneUniqueID", 0, false);
			ArrayList<Integer> pickupUniqueIDs = new ArrayList<>();
			LinkedHashMap<String, Integer> expectedAmounts = new LinkedHashMap<>();
			for (LoadData pickup : pending.getLoadDataByName("PICKUP")) {
				int uniqueID = pickup.getInt("uniqueID", 0, false);
				if (uniqueID != 0) pickupUniqueIDs.add(uniqueID);
			}
			for (LoadData item : pending.getLoadDataByName("EXPECTED_ITEM")) {
				String itemStringID = item.getSafeString("itemStringID", "", false);
				int amount = Math.max(0, item.getInt("amount", 0, false));
				if (!itemStringID.isEmpty() && amount > 0) expectedAmounts.put(itemStringID, amount);
			}
			pendingBatches.put(getKey(tileX, tileY), new PendingBatch(zoneUniqueID, pickupUniqueIDs, expectedAmounts));
		}
	}

	@Override
	public void onLoadingComplete() {
		super.onLoadingComplete();
		for (Map.Entry<Long, PendingBatch> entry : new ArrayList<>(pendingBatches.entrySet())) {
			int tileX = (int)(entry.getKey() >> 32);
			int tileY = (int)(long)entry.getKey();
			if (!level.isTileWithinBounds(tileX, tileY)) {
				pendingBatches.remove(entry.getKey());
				continue;
			}
			boolean exists = level.jobsLayer.streamJobsInTile(tileX, tileY)
					.anyMatch(job -> job instanceof ClayFiringCleanupLevelJob);
			if (!exists) level.jobsLayer.addJob(new ClayFiringCleanupLevelJob(tileX, tileY, entry.getValue().zoneUniqueID));
		}
	}

	public ClayFiringZone findZone(int tileX, int tileY, int preferredZoneUniqueID) {
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(level)
				.getOrLoadServerDataAtTile(level.getIdentifier(), tileX, tileY);
		if (settlement == null) return null;

		if (preferredZoneUniqueID != 0) {
			Object preferred = settlement.getWorkZones().getZones().get(preferredZoneUniqueID);
			if (preferred instanceof ClayFiringZone) {
				ClayFiringZone zone = (ClayFiringZone)preferred;
				if (!zone.isRemoved() && zone.containsTile(tileX, tileY)) return zone;
			}
		}
		for (Object value : settlement.getWorkZones().getZones().values()) {
			SettlementWorkZone workZone = (SettlementWorkZone)value;
			if (workZone instanceof ClayFiringZone && !workZone.isRemoved() && workZone.containsTile(tileX, tileY)) {
				return (ClayFiringZone)workZone;
			}
		}
		return null;
	}

	private static long getKey(int tileX, int tileY) {
		return (long)tileX << 32 | tileY & 0xFFFFFFFFL;
	}

	public static class PendingBatch {
		public final int zoneUniqueID;
		public final List<Integer> pickupUniqueIDs;
		public final Map<String, Integer> expectedAmounts;

		public PendingBatch(int zoneUniqueID, List<Integer> pickupUniqueIDs, Map<String, Integer> expectedAmounts) {
			this.zoneUniqueID = zoneUniqueID;
			this.pickupUniqueIDs = pickupUniqueIDs == null ? new ArrayList<>() : new ArrayList<>(pickupUniqueIDs);
			this.expectedAmounts = expectedAmounts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(expectedAmounts);
		}

		public List<InventoryItem> getExpectedItems() {
			ArrayList<InventoryItem> result = new ArrayList<>();
			for (Map.Entry<String, Integer> entry : expectedAmounts.entrySet()) {
				if (entry.getValue() > 0 && ItemRegistry.getItem(entry.getKey()) != null) {
					result.add(new InventoryItem(entry.getKey(), entry.getValue()));
				}
			}
			return result;
		}
	}
}
