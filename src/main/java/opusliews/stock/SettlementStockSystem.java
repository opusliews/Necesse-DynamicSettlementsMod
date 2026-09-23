package opusliews.stock;

import necesse.engine.registries.ItemRegistry;
import necesse.engine.util.GameLinkedList;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.inventory.InventoryAddConsumer;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.item.Item;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import necesse.level.maps.levelData.jobs.HaulFromLevelJob;
import necesse.level.maps.levelData.settlementData.LevelStorage;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import necesse.level.maps.levelData.settlementData.SettlementStockInventoryAccess;
import necesse.level.maps.levelData.settlementData.SettlementStoragePickupFuture;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageItemIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecord;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecordsRegionData;
import necesse.inventory.container.settlement.events.SettlementStorageChangeAllowedEvent;
import necesse.inventory.container.settlement.events.SettlementStoragePriorityLimitEvent;
import opusliews.logging.Logging;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class SettlementStockSystem {
	private static final String saveKey = "dynamicStocking";
	private static final String entryKey = "item";
	private static final int stockPriority = 1000000;
	private static final WeakHashMap<SettlementInventory, StorageState> states = new WeakHashMap<>();
	private static final WeakHashMap<ItemCategoriesFilter, WeakReference<SettlementInventory>> filterOwners = new WeakHashMap<>();

	private SettlementStockSystem() {
	}

	public static synchronized StorageState getState(SettlementInventory storage) {
		if (storage == null) return null;
		WeakReference<SettlementInventory> owner = filterOwners.get(storage.filter);
		if (owner == null || owner.get() != storage) filterOwners.put(storage.filter, new WeakReference<>(storage));
		return states.computeIfAbsent(storage, ignored -> new StorageState());
	}

	public static synchronized Map<Integer, Integer> getTargets(SettlementInventory storage) {
		StorageState state = getState(storage);
		return state == null ? new LinkedHashMap<>() : new LinkedHashMap<>(state.targets);
	}

	public static synchronized int getStockTarget(SettlementInventory storage, int itemID) {
		StorageState state = getState(storage);
		return state == null ? 0 : Math.max(0, state.targets.getOrDefault(itemID, 0));
	}

	public static synchronized boolean hasAnyStock(SettlementInventory storage) {
		StorageState state = getState(storage);
		return state != null && state.targets.values().stream().anyMatch(value -> value != null && value > 0);
	}

	public static synchronized boolean setStockTarget(SettlementInventory storage, int itemID, int stock) {
		if (storage == null || ItemRegistry.getItem(itemID) == null) return false;
		StorageState state = getState(storage);
		stock = Math.max(0, stock);
		int previous = state.targets.getOrDefault(itemID, 0);
		if (stock <= 0) {
			state.targets.remove(itemID);
			state.restocking.remove(itemID);
		} else {
			state.targets.put(itemID, stock);
		}
		return previous != stock;
	}

	public static synchronized boolean isRestocking(SettlementInventory storage, int itemID) {
		StorageState state = getState(storage);
		return state != null && state.restocking.containsKey(itemID);
	}

	public static int getRestockThreshold(int stock) {
		return stock <= 0 ? 0 : Math.max(1, stock / 2);
	}

	private static synchronized SettlementInventory getFilterOwner(ItemCategoriesFilter filter) {
		WeakReference<SettlementInventory> owner = filterOwners.get(filter);
		return owner == null ? null : owner.get();
	}

	public static boolean isStockDestination(HaulFromLevelJob.HaulPosition position) {
		return position instanceof StockHaulPosition;
	}

	public static int getRemainingStockDemand(HaulFromLevelJob.HaulPosition position, InventoryItem item) {
		if (item == null || item.getAmount() <= 0) return 0;
		if (!isStockDestination(position)) return item.getAmount();
		SettlementInventory storage = (SettlementInventory)position.storage;
		int itemID = item.item.getID();
		int target = getStockTarget(storage, itemID);
		if (target <= 0 || !isRestocking(storage, itemID)) return 0;

		storage.canAddFutureDropOff(item);
		int effectiveCount = storage.getItemCount(
				inventoryItem -> inventoryItem != null && inventoryItem.item.getID() == itemID,
				Integer.MAX_VALUE,
				true
		);
		return Math.min(item.getAmount(), Math.max(0, target - effectiveCount));
	}

	public static void save(SettlementInventory storage, SaveData save) {
		if (storage == null || save == null) return;
		StorageState state;
		synchronized (SettlementStockSystem.class) {
			state = states.get(storage);
		}
		if (state == null || state.targets.isEmpty()) return;

		SaveData root = new SaveData(saveKey);
		for (Map.Entry<Integer, Integer> entry : state.targets.entrySet()) {
			Item item = ItemRegistry.getItem(entry.getKey());
			int stock = Math.max(0, entry.getValue());
			if (item == null || stock <= 0) continue;
			SaveData itemSave = new SaveData(entryKey);
			itemSave.addItemStringID("id", item.getStringID());
			itemSave.addInt("stock", stock);
			itemSave.addBoolean("restocking", state.restocking.containsKey(entry.getKey()));
			root.addSaveData(itemSave);
		}
		if (!root.isEmpty()) save.addSaveData(root);
	}

	public static void load(SettlementInventory storage, LoadData save) {
		if (storage == null || save == null) return;
		StorageState state = getState(storage);
		state.targets.clear();
		state.restocking.clear();
		LoadData root = save.getFirstLoadDataByName(saveKey);
		if (root == null) return;

		for (LoadData itemData : root.getLoadDataByName(entryKey)) {
			String stringID = itemData.getItemStringID("id", null, false);
			Item item = stringID == null ? null : ItemRegistry.getItem(stringID);
			if (item == null) continue;
			int stock = Math.max(0, itemData.getInt("stock", 0, false));
			if (stock <= 0) continue;
			state.targets.put(item.getID(), stock);
			if (itemData.getBoolean("restocking", false, false)) state.restocking.put(item.getID(), true);
		}
	}

	public static int getRequiredConfiguredMax(SettlementInventory storage) {
		if (storage == null || storage.filter.maxAmount == Integer.MAX_VALUE) return Integer.MAX_VALUE;
		StorageState state = getState(storage);
		if (state == null || state.targets.isEmpty()) return 0;

		long value = 0;
		switch (storage.filter.limitMode) {
			case TOTAL_ITEMS:
				for (int stock : state.targets.values()) value += Math.max(0, stock);
				break;
			case TOTAL_EACH_ITEM:
				for (int stock : state.targets.values()) value = Math.max(value, Math.max(0, stock));
				break;
			case TOTAL_STACKS:
				for (Map.Entry<Integer, Integer> entry : state.targets.entrySet()) {
					Item item = ItemRegistry.getItem(entry.getKey());
					if (item == null) continue;
					int stackSize = Math.max(1, item.getStackSize());
					value += (Math.max(0, entry.getValue()) + stackSize - 1L) / stackSize;
				}
				break;
			case TOTAL_STACKS_EACH_ITEM:
				for (Map.Entry<Integer, Integer> entry : state.targets.entrySet()) {
					Item item = ItemRegistry.getItem(entry.getKey());
					if (item == null) continue;
					int stackSize = Math.max(1, item.getStackSize());
					long stacks = (Math.max(0, entry.getValue()) + stackSize - 1L) / stackSize;
					value = Math.max(value, stacks);
				}
				break;
		}
		return (int)Math.min(Integer.MAX_VALUE, value);
	}

	public static boolean enforceConfiguredMax(ServerSettlementData settlement, SettlementInventory storage, boolean sendEvent) {
		if (storage == null || storage.filter.maxAmount == Integer.MAX_VALUE) return false;
		int required = getRequiredConfiguredMax(storage);
		if (required <= 0 || storage.filter.maxAmount >= required) return false;
		storage.filter.maxAmount = required;
		if (sendEvent && settlement != null) {
			new SettlementStoragePriorityLimitEvent(
					settlement,
					storage.tileX,
					storage.tileY,
					storage.filter.limitMode,
					required
			).applyAndSendToClientsAt(settlement.getLevel());
		}
		return true;
	}

	public static int overrideAddAmount(
			ItemCategoriesFilter filter,
			InventoryItem item,
			InventoryRange range,
			int vanillaAmount
	) {
		if (filter == null || item == null || range == null) return vanillaAmount;
		SettlementInventory storage = getFilterOwner(filter);
		if (storage == null || !filter.isItemAllowed(item.item)) return vanillaAmount;
		StorageState state = getState(storage);
		int itemID = item.item.getID();
		if (state == null || !state.restocking.containsKey(itemID)) return vanillaAmount;
		int target = state.targets.getOrDefault(itemID, 0);
		if (target <= 0) return vanillaAmount;

		int current = countItem(range, itemID, target);
		int needed = Math.max(0, target - current);
		if (needed <= 0) return vanillaAmount;
		return Math.max(vanillaAmount, Math.min(item.getAmount(), needed));
	}


	public static int overrideRemoveAmount(
			ItemCategoriesFilter filter,
			InventoryItem item,
			InventoryRange range,
			int vanillaAmount
	) {
		if (vanillaAmount <= 0 || filter == null || item == null || range == null) return vanillaAmount;
		SettlementInventory storage = getFilterOwner(filter);
		if (storage == null) return vanillaAmount;

		int itemID = item.item.getID();
		int target = getStockTarget(storage, itemID);
		if (target <= 0) return vanillaAmount;

		int simulatedCount = countItem(range, itemID, Integer.MAX_VALUE);
		int unreservedCount = countFutureUnreservedItem(storage, itemID);
		int protectedSurplus = Math.max(0, Math.min(simulatedCount, unreservedCount) - target);
		int result = Math.min(vanillaAmount, Math.min(item.getAmount(), protectedSurplus));
		if (Logging.logEnabled) {
			Logging.logMessage("[StockRemove] chest=" + storage.tileX + "," + storage.tileY
					+ " item=" + item.item.getStringID()
					+ " requested=" + item.getAmount()
					+ " vanilla=" + vanillaAmount
					+ " simulated=" + simulatedCount
					+ " unreserved=" + unreservedCount
					+ " stock=" + target
					+ " allowed=" + result);
		}
		return result;
	}

	public static int getMaxRemovable(LevelStorage storage, InventoryItem item) {
		if (!(storage instanceof SettlementInventory) || item == null) return item == null ? 0 : item.getAmount();
		SettlementInventory settlementStorage = (SettlementInventory)storage;
		int target = getStockTarget(settlementStorage, item.item.getID());
		if (target <= 0) return item.getAmount();

		int unreserved = countFutureUnreservedItem(storage, item.item.getID());
		int result = Math.min(item.getAmount(), Math.max(0, unreserved - target));
		if (Logging.logEnabled) {
			Logging.logMessage("[StockGuard] chest=" + settlementStorage.tileX + "," + settlementStorage.tileY
					+ " item=" + item.item.getStringID()
					+ " requested=" + item.getAmount()
					+ " unreserved=" + unreserved
					+ " stock=" + target
					+ " allowed=" + result);
		}
		return result;
	}

	public static void tickStockJobs(ServerSettlementData settlement) {
		if (settlement == null || settlement.storageRecords == null) return;
		ArrayList<SettlementInventory> storages = new ArrayList<>();
		for (Object value : settlement.storageManager.getStorage()) {
			if (value instanceof SettlementInventory) storages.add((SettlementInventory)value);
		}
		storages.sort(Comparator.comparingInt((SettlementInventory storage) -> storage.tileY)
				.thenComparingInt(storage -> storage.tileX));

		for (SettlementInventory storage : storages) {
			getState(storage);
			ensureStockedItemsAllowed(settlement, storage);
			enforceConfiguredMax(settlement, storage, true);
		}

		HashMap<Integer, StockSourcePool> sourcePools = new HashMap<>();
		IdentityHashMap<HaulFromLevelJob, Integer> plannedByJob = new IdentityHashMap<>();

		for (SettlementInventory destination : storages) {
			StorageState state = getState(destination);
			if (state == null || state.targets.isEmpty()) continue;
			InventoryRange destinationRange = destination.getInventoryRange();
			if (destinationRange == null || destination.isAllAdjacentSolid()) continue;
			StockDestinationCapacity destinationCapacity = new StockDestinationCapacity(destination);

			for (Map.Entry<Integer, Integer> targetEntry : new ArrayList<>(state.targets.entrySet())) {
				int itemID = targetEntry.getKey();
				int stock = Math.max(0, targetEntry.getValue());
				Item item = ItemRegistry.getItem(itemID);
				if (item == null || stock <= 0) continue;

				int physicalCount = countItem(destinationRange, itemID, Integer.MAX_VALUE);
				int threshold = getRestockThreshold(stock);
				boolean active = state.restocking.containsKey(itemID);
				if (Logging.logEnabled) {
					Logging.logMessage("[StockTick] chest=" + destination.tileX + "," + destination.tileY
							+ " item=" + item.getStringID()
							+ " physical=" + physicalCount
							+ " restock=" + threshold
							+ " stock=" + stock
							+ " activeBefore=" + active);
				}
				if (active && physicalCount >= stock) {
					state.restocking.remove(itemID);
					if (Logging.logEnabled) {
						Logging.logMessage("[StockTransition] STOP chest=" + destination.tileX + "," + destination.tileY
								+ " item=" + item.getStringID()
								+ " physical=" + physicalCount
								+ " stock=" + stock);
					}
					continue;
				}
				if (!active && physicalCount < threshold) {
					state.restocking.put(itemID, true);
					active = true;
					if (Logging.logEnabled) {
						Logging.logMessage("[StockTransition] START chest=" + destination.tileX + "," + destination.tileY
								+ " item=" + item.getStringID()
								+ " physical=" + physicalCount
								+ " restock=" + threshold
								+ " stock=" + stock);
					}
				}
				if (!active) {
					if (Logging.logEnabled) {
						Logging.logMessage("[StockDecision] IDLE chest=" + destination.tileX + "," + destination.tileY
								+ " item=" + item.getStringID()
								+ " physical=" + physicalCount
								+ " restock=" + threshold
								+ " stock=" + stock);
					}
					continue;
				}

				int effectiveCount = destinationCapacity.getItemCount(itemID);
				int pendingCount = Math.max(0, effectiveCount - physicalCount);
				int deficit = Math.max(0, stock - effectiveCount);
				if (Logging.logEnabled) {
					Logging.logMessage("[StockDecision] ACTIVE chest=" + destination.tileX + "," + destination.tileY
							+ " item=" + item.getStringID()
							+ " physical=" + physicalCount
							+ " effective=" + effectiveCount
							+ " pending=" + pendingCount
							+ " deficit=" + deficit
							+ " stock=" + stock);
				}
				if (deficit <= 0) continue;

				StockSourcePool sourcePool = sourcePools.computeIfAbsent(
						itemID,
						ignored -> buildSourcePool(settlement, itemID)
				);

				ArrayList<SourceItemGroup> sourceGroups = new ArrayList<>(sourcePool.groups);
				sourceGroups.sort(Comparator
						.comparingInt((SourceItemGroup group) -> group.source.priority)
						.thenComparingLong(group -> distanceSquared(group.source, destination))
						.thenComparingInt(group -> group.source.tileY)
						.thenComparingInt(group -> group.source.tileX));

				for (SourceItemGroup sourceGroup : sourceGroups) {
					if (deficit <= 0) break;
					SettlementInventory source = sourceGroup.source;
					if (source == destination || source.isAllAdjacentSolid()) continue;

					int sourceBudget = sourcePool.remainingBySource.getOrDefault(source, 0);
					if (sourceBudget <= 0 || sourceGroup.remainingAmount <= 0) continue;
					int amount = Math.min(deficit, Math.min(sourceBudget, sourceGroup.remainingAmount));
					amount = destinationCapacity.getCanPlanAmount(sourceGroup.item.copy(amount));
					if (amount <= 0) continue;

					HaulFromLevelJob job = findHaulJob(settlement, source, sourceGroup.item);
					boolean created = job == null;
					if (created) job = new HaulFromLevelJob(source, sourceGroup.item.copy(amount));

					if (hasStockDestination(job, destination)) continue;

					int planned = plannedByJob.getOrDefault(job, 0) + amount;
					plannedByJob.put(job, planned);
					job.item.setAmount(Math.max(job.item.getAmount(), planned));
					job.dropOffPositions.add(new StockHaulPosition(destination, amount));
					if (Logging.logEnabled) {
						Logging.logMessage("[StockSchedule] source=" + source.tileX + "," + source.tileY
								+ " destination=" + destination.tileX + "," + destination.tileY
								+ " item=" + item.getStringID()
								+ " amount=" + amount
								+ " physical=" + physicalCount
								+ " effective=" + effectiveCount
								+ " deficitBefore=" + deficit
								+ " active=" + active);
					}

					if (created) {
						HaulFromLevelJob added = (HaulFromLevelJob)settlement.getLevel().jobsLayer.addJob(job, true);
						if (added == null) {
							plannedByJob.remove(job);
							if (Logging.logEnabled) Logging.logMessage("[Stock] Failed to add haul job item=" + item.getStringID()
									+ " source=" + source.tileX + "," + source.tileY
									+ " destination=" + destination.tileX + "," + destination.tileY
									+ " amount=" + amount);
							continue;
						}
						job = added;
					}

					destinationCapacity.addPlanned(sourceGroup.item.copy(amount));
					sourcePool.remainingBySource.put(source, sourceBudget - amount);
					sourceGroup.remainingAmount -= amount;
					deficit -= amount;
				}
			}
		}
	}

	private static StockSourcePool buildSourcePool(ServerSettlementData settlement, int itemID) {
		StockSourcePool pool = new StockSourcePool();
		SettlementStorageItemIDIndex itemIndex = settlement.storageRecords.getIndex(SettlementStorageItemIDIndex.class);
		SettlementStorageRecordsRegionData recordsData = itemIndex.getItem(itemID);
		if (recordsData == null) return pool;

		recordsData.streamAllRecords()
				.flatMap(region -> region.values().stream())
				.flatMap(GameLinkedList::stream)
				.forEach(recordValue -> {
					SettlementStorageRecord record = (SettlementStorageRecord)recordValue;
					if (!(record.storage instanceof SettlementInventory) || record.itemAmount <= 0) return;
					SettlementInventory source = (SettlementInventory)record.storage;
					InventoryItem recordItem = record.getItem();
					if (recordItem == null || recordItem.item.getID() != itemID) return;

					SourceItemGroup group = null;
					for (SourceItemGroup candidate : pool.groups) {
						if (candidate.source != source) continue;
						if (!candidate.item.equals(settlement.getLevel(), recordItem, true, false, "stocking")) continue;
						group = candidate;
						break;
					}
					if (group == null) {
						group = new SourceItemGroup(source, recordItem.copy(1));
						pool.groups.add(group);
					}
					group.remainingAmount += record.itemAmount;
					pool.remainingBySource.merge(source, record.itemAmount, Integer::sum);
				});

		for (Map.Entry<SettlementInventory, Integer> entry : pool.remainingBySource.entrySet()) {
			int protectedAmount = getStockTarget(entry.getKey(), itemID);
			entry.setValue(Math.max(0, entry.getValue() - protectedAmount));
		}

		return pool;
	}

	private static HaulFromLevelJob findHaulJob(
			ServerSettlementData settlement,
			SettlementInventory source,
			InventoryItem sourceItem
	) {
		return (HaulFromLevelJob)settlement.getLevel().jobsLayer
				.streamJobsInTile(source.tileX, source.tileY)
				.filter(job -> job instanceof HaulFromLevelJob)
				.map(job -> (HaulFromLevelJob)job)
				.filter(job -> job.item != null && job.item.equals(settlement.getLevel(), sourceItem, true, false, "stocking"))
				.findFirst()
				.orElse(null);
	}

	private static boolean hasStockDestination(HaulFromLevelJob job, SettlementInventory destination) {
		for (Object positionValue : job.dropOffPositions) {
			HaulFromLevelJob.HaulPosition position = (HaulFromLevelJob.HaulPosition)positionValue;
			if (!isStockDestination(position)) continue;
			if (position.storage == destination
					|| (position.storage.tileX == destination.tileX && position.storage.tileY == destination.tileY)) {
				return true;
			}
		}
		return false;
	}

	private static void ensureStockedItemsAllowed(ServerSettlementData settlement, SettlementInventory storage) {
		StorageState state = getState(storage);
		for (Map.Entry<Integer, Integer> entry : new ArrayList<>(state.targets.entrySet())) {
			if (entry.getValue() == null || entry.getValue() <= 0) continue;
			Item item = ItemRegistry.getItem(entry.getKey());
			if (item == null || storage.filter.isItemAllowed(item)) continue;
			storage.filter.setItemAllowed(item, true);
			new SettlementStorageChangeAllowedEvent(
					settlement,
					storage.tileX,
					storage.tileY,
					new Item[]{item},
					true
			).applyAndSendToClientsAt(settlement.getLevel());
		}
	}


	private static long distanceSquared(SettlementInventory source, SettlementInventory destination) {
		long dx = source.tileX - destination.tileX;
		long dy = source.tileY - destination.tileY;
		return dx * dx + dy * dy;
	}

	private static int countFutureUnreservedItem(LevelStorage storage, int itemID) {
		if (storage == null) return 0;
		int amount = 0;
		Iterator<?> iterator = storage.findFutureUnreservedSlots().iterator();
		while (iterator.hasNext()) {
			Object next = iterator.next();
			if (!(next instanceof SettlementStoragePickupFuture)) continue;
			InventoryItem available = ((SettlementStoragePickupFuture)next).item;
			if (available != null && available.item.getID() == itemID) amount += available.getAmount();
		}
		return amount;
	}

	private static int countItem(InventoryRange range, int itemID, int stopAt) {
		if (range == null) return 0;
		int amount = 0;
		for (int slot = range.startSlot; slot <= range.endSlot; slot++) {
			InventoryItem item = range.inventory.getItem(slot);
			if (item == null || item.item.getID() != itemID) continue;
			amount += item.getAmount();
			if (amount >= stopAt) return amount;
		}
		return amount;
	}

	private static final class StockHaulPosition extends HaulFromLevelJob.HaulPosition {
		private StockHaulPosition(SettlementInventory storage, int amount) {
			super(storage, stockPriority, amount);
		}
	}

	private static final class StockDestinationCapacity {
		private final SettlementInventory storage;
		private final InventoryRange plannedRange;

		private StockDestinationCapacity(SettlementInventory storage) {
			this.storage = storage;
			this.plannedRange = SettlementStockInventoryAccess.copyFutureDropOffRange(storage);
		}

		private int getItemCount(int itemID) {
			return countItem(plannedRange, itemID, Integer.MAX_VALUE);
		}

		private int getCanPlanAmount(InventoryItem item) {
			if (plannedRange == null || item.getAmount() <= 0) return 0;
			int filterAmount = storage.filter.getAddAmount(storage.level, item, plannedRange, false);
			int inventoryAmount = plannedRange.inventory.canAddItem(
					storage.level, (PlayerMob)null, item, plannedRange.startSlot, plannedRange.endSlot, "stocking"
			);
			return Math.min(item.getAmount(), Math.min(filterAmount, inventoryAmount));
		}

		private void addPlanned(InventoryItem item) {
			plannedRange.inventory.addItem(
					storage.level, (PlayerMob)null, item, plannedRange.startSlot, plannedRange.endSlot,
					"stocking", (InventoryAddConsumer)null
			);
		}
	}

	private static final class StockSourcePool {
		private final ArrayList<SourceItemGroup> groups = new ArrayList<>();
		private final IdentityHashMap<SettlementInventory, Integer> remainingBySource = new IdentityHashMap<>();
	}

	private static final class SourceItemGroup {
		private final SettlementInventory source;
		private final InventoryItem item;
		private int remainingAmount;

		private SourceItemGroup(SettlementInventory source, InventoryItem item) {
			this.source = source;
			this.item = item;
		}
	}

	public static final class StorageState {
		private final LinkedHashMap<Integer, Integer> targets = new LinkedHashMap<>();
		private final HashMap<Integer, Boolean> restocking = new HashMap<>();
	}
}
