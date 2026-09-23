package opusliews.jobs;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.util.ComputedObjectValue;
import necesse.engine.util.ComputedValue;
import necesse.engine.util.GameObjectReservable;
import necesse.engine.util.PointHashMap;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.entity.mobs.job.LinkedListJobSequence;
import necesse.entity.mobs.job.activeJob.DropOffSettlementStorageActiveJob;
import necesse.entity.mobs.job.activeJob.PerformLevelJobEventActiveJob;
import necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob;
import necesse.inventory.InventoryAddConsumer;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import necesse.level.maps.levelData.jobs.HaulFromLevelJob;
import necesse.level.maps.levelData.settlementData.LevelStorage;
import necesse.level.maps.levelData.settlementData.SettlementStoragePickupSlot;
import necesse.level.maps.levelData.settlementData.SettlementRequestInventory;
import necesse.level.maps.levelData.settlementData.ZoneTester;
import opusliews.clay.ClayPackageSystem;
import opusliews.logging.Logging;
import opusliews.mobs.BuilderHumanMob;
import opusliews.stock.SettlementStockSystem;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class BatchedHaulingSystem {
	private static final int vanillaWorkInventoryStacks = 5;

	private BatchedHaulingSystem() {
	}

	public static JobSequence getBatchedJobSequence(
			HaulFromLevelJob primary,
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority
	) {
		if (primary == null || worker == null || primary.item == null) return null;

		InventoryRange sourceRange = primary.getInventoryRange();
		if (sourceRange == null) return null;

		HaulFromLevelJob.HaulPosition destination = selectDestination(primary, worker);
		if (destination == null) return null;

		DestinationCapacity destinationCapacity = new DestinationCapacity(destination.storage);
		BatchCapacity capacity = new BatchCapacity(worker);
		int primaryCapacity = Math.min(
				capacity.getCanPlanAmount(primary.item),
				getSourceRemovalLimit(primary, destination.storage)
		);
		if (primaryCapacity <= 0) return null;

		int primaryAmount = Math.min(
				getReservableAmount(primary, destination, destinationCapacity),
				primaryCapacity
		);
		if (primaryAmount <= 0) return null;

		LinkedList<SettlementStoragePickupSlot> primarySlots = findExactUnreservedSlots(
				primary,
				primaryAmount
		);
		if (primarySlots == null || primarySlots.isEmpty()) return null;

		GameMessage targetDescription = primary.storage.getInventoryName();
		GameMessage itemDescription = primarySlots.getFirst().item.getItemLocalization();
		GameMessage activityDescription = new LocalMessage(
				"activities",
				"hauling",
				new Object[]{"item", itemDescription, "target", targetDescription}
		);
		LinkedListJobSequence sequence = new LinkedListJobSequence(activityDescription, false);

		int reservedPrimary = addJobToSequence(sequence, worker, priority, primary, destination.storage, primarySlots);
		if (reservedPrimary <= 0) {
			removeSlots(primarySlots);
			return null;
		}
		InventoryItem primaryPlanned = primarySlots.getFirst().item.copy(reservedPrimary);
		capacity.addPlanned(primaryPlanned);
		destinationCapacity.addPlanned(primaryPlanned);

		if (Logging.logEnabled) {
			Logging.logMessage("[BatchedHauling] Primary source=" + primary.storage.tileX + "," + primary.storage.tileY
					+ " item=" + primary.item.item.getStringID()
					+ " reserved=" + reservedPrimary
					+ " clay=" + ClayPackageSystem.isClayItem(primary.item)
					+ " destination=" + destination.storage.tileX + "," + destination.storage.tileY
					+ " destinationType=" + destination.storage.getClass().getSimpleName());
		}

		List<HaulFromLevelJob> siblings = worker.streamValidJobsWithinRange(null)
				.filter(job -> job instanceof HaulFromLevelJob)
				.map(job -> (HaulFromLevelJob)job)
				.filter(job -> job != primary)
				.filter(job -> job.storage == primary.storage
						|| (job.storage.tileX == primary.storage.tileX && job.storage.tileY == primary.storage.tileY))
				.filter(job -> job.item != null && job.item.getAmount() > 0)
				.filter(job -> getMatchingDestination(job, destination.storage, worker) != null)
				.collect(Collectors.toList());

		for (HaulFromLevelJob sibling : siblings) {
			if (Logging.logEnabled) {
				Logging.logMessage("[BatchedHauling] Candidate item=" + sibling.item.item.getStringID()
						+ " clay=" + ClayPackageSystem.isClayItem(sibling.item)
						+ " unfired=" + ClayPackageSystem.isUnfiredClayItem(sibling.item.item)
						+ " fired=" + ClayPackageSystem.isFiredClayItem(sibling.item.item));
			}
			if (!capacity.canPlan(sibling.item)) {
				if (Logging.logEnabled) Logging.logMessage("[BatchedHauling] Candidate rejected by worker capacity item="
						+ sibling.item.item.getStringID());
				continue;
			}

			HaulFromLevelJob.HaulPosition siblingDestination = getMatchingDestination(sibling, destination.storage, worker);
			if (siblingDestination == null) continue;

			int capacityAmount = Math.min(
					capacity.getCanPlanAmount(sibling.item),
					getSourceRemovalLimit(sibling, siblingDestination.storage)
			);
			if (capacityAmount <= 0) {
				if (Logging.logEnabled) Logging.logMessage("[BatchedHauling] Candidate rejected after destination match: no worker capacity item="
						+ sibling.item.item.getStringID());
				continue;
			}

			int amount = Math.min(
					getReservableAmount(sibling, siblingDestination, destinationCapacity),
					capacityAmount
			);
			if (amount <= 0) continue;

			LinkedList<SettlementStoragePickupSlot> slots = findExactUnreservedSlots(
					sibling,
					amount
			);
			if (slots == null || slots.isEmpty()) {
				if (Logging.logEnabled) Logging.logMessage("[BatchedHauling] Candidate rejected: no unreserved source slots item="
						+ sibling.item.item.getStringID() + " requested=" + amount);
				continue;
			}

			int reserved = addJobToSequence(sequence, worker, priority, sibling, destination.storage, slots);
			if (reserved <= 0) {
				if (Logging.logEnabled) Logging.logMessage("[BatchedHauling] Candidate rejected: sequence reservation produced zero item="
						+ sibling.item.item.getStringID());
				removeSlots(slots);
				continue;
			}

			InventoryItem planned = slots.getFirst().item.copy(reserved);
			capacity.addPlanned(planned);
			destinationCapacity.addPlanned(planned);
			if (Logging.logEnabled) Logging.logMessage("[BatchedHauling] Added candidate item=" + sibling.item.item.getStringID()
					+ " amount=" + reserved + " destination=" + destination.storage.tileX + "," + destination.storage.tileY);
		}

		return sequence;
	}


	public static boolean shouldPreventVanillaFallback(HaulFromLevelJob job) {
		if (job == null || job.storage == null || job.item == null || job.item.getAmount() <= 0) return false;

		boolean hasOrdinaryDestination = false;
		for (Object value : job.dropOffPositions) {
			HaulFromLevelJob.HaulPosition pos = (HaulFromLevelJob.HaulPosition)value;
			if (SettlementStockSystem.isStockDestination(pos)) return true;
			if (!(pos.storage instanceof SettlementRequestInventory)) {
				hasOrdinaryDestination = true;
			}
		}
		if (!hasOrdinaryDestination) return false;

		int allowed = SettlementStockSystem.getMaxRemovable(job.storage, job.item);
		boolean prevent = allowed < job.item.getAmount();
		if (prevent && Logging.logEnabled) {
			Logging.logMessage("[BatchedHauling] Blocking vanilla fallback source="
					+ job.storage.tileX + "," + job.storage.tileY
					+ " item=" + job.item.item.getStringID()
					+ " requested=" + job.item.getAmount()
					+ " allowed=" + allowed);
		}
		return prevent;
	}

	private static int getSourceRemovalLimit(HaulFromLevelJob job, LevelStorage destination) {
		if (destination instanceof SettlementRequestInventory) return job.item.getAmount();
		return SettlementStockSystem.getMaxRemovable(job.storage, job.item);
	}

	private static LinkedList<SettlementStoragePickupSlot> findExactUnreservedSlots(
			HaulFromLevelJob job,
			int amount
	) {
		if (job == null || job.storage == null || job.item == null || amount <= 0) return null;

		return job.storage.findUnreservedSlots(
				invItem -> job.item.equals(job.getLevel(), invItem, true, false, "batchedhaul-pickup"),
				amount,
				amount
		);
	}

	private static HaulFromLevelJob.HaulPosition selectDestination(HaulFromLevelJob job, EntityJobWorker worker) {
		ZoneTester restrictZone = worker.getJobRestrictZone();
		PointHashMap<Boolean> estimatedCanMoveToCache = new PointHashMap<>();
		ArrayList<HaulFromLevelJob.HaulPosition> valid = new ArrayList<>();

		for (Object value : job.dropOffPositions) {
			HaulFromLevelJob.HaulPosition pos = (HaulFromLevelJob.HaulPosition)value;
			if (!restrictZone.containsTile(pos.storage.tileX, pos.storage.tileY)) continue;
			if (getSourceRemovalLimit(job, pos.storage) <= 0) continue;
			if (!canUseDestination(job, pos, worker, estimatedCanMoveToCache)) continue;
			valid.add(pos);
		}

		if (valid.isEmpty()) return null;

		Comparator<ComputedObjectValue<HaulFromLevelJob.HaulPosition, Double>> comparator =
				Comparator.comparingInt(value -> -value.object.priority);
		comparator = comparator.thenComparingDouble(ComputedValue::get);

		Point source = new Point(job.tileX * 32 + 16, job.tileY * 32 + 16);
		return valid.stream()
				.map(pos -> new ComputedObjectValue<>(pos, () -> source.distance(
						pos.storage.tileX * 32 + 16,
						pos.storage.tileY * 32 + 16
				)))
				.min(comparator)
				.map(value -> value.object)
				.orElse(null);
	}

	private static HaulFromLevelJob.HaulPosition getMatchingDestination(
			HaulFromLevelJob job,
			LevelStorage destination,
			EntityJobWorker worker
	) {
		ZoneTester restrictZone = worker.getJobRestrictZone();
		if (!restrictZone.containsTile(destination.tileX, destination.tileY)) {
			if (Logging.logEnabled) Logging.logMessage("[BatchedHauling] Candidate rejected: destination outside restrict zone item="
					+ job.item.item.getStringID());
			return null;
		}

		for (Object value : job.dropOffPositions) {
			HaulFromLevelJob.HaulPosition pos = (HaulFromLevelJob.HaulPosition)value;
			if (pos.storage != destination
					&& (pos.storage.tileX != destination.tileX || pos.storage.tileY != destination.tileY)) {
				continue;
			}
			if (getDestinationAmount(job, pos) <= 0) continue;

			if (pos.getInventoryRange() == null) {
				if (Logging.logEnabled) Logging.logMessage("[BatchedHauling] Candidate rejected: destination inventory unavailable item="
						+ job.item.item.getStringID());
				return null;
			}
			if (!pos.storage.estimateCanMoveTo(worker)) {
				if (Logging.logEnabled) Logging.logMessage("[BatchedHauling] Candidate rejected: destination unreachable item="
						+ job.item.item.getStringID());
				return null;
			}

			return pos;
		}

		if (Logging.logEnabled) Logging.logMessage("[BatchedHauling] Candidate rejected: primary destination is not valid for item="
				+ job.item.item.getStringID() + " destination=" + destination.tileX + "," + destination.tileY);
		return null;
	}

	private static boolean canUseDestination(
			HaulFromLevelJob job,
			HaulFromLevelJob.HaulPosition pos,
			EntityJobWorker worker,
			PointHashMap<Boolean> estimatedCanMoveToCache
	) {
		InventoryRange range = pos.getInventoryRange();
		if (range == null) return false;
		if (getDestinationAmount(job, pos) <= 0) return false;

		int filterAmount = pos.storage.getFilter().getAddAmount(job.getLevel(), job.item, range, false);
		if (filterAmount <= 0) return false;
		if (range.inventory.canAddItem(
				job.getLevel(),
				(PlayerMob)null,
				job.item,
				range.startSlot,
				range.endSlot,
				"hauljob"
			) <= 0) return false;

		return estimatedCanMoveToCache.compute(
				pos.storage.tileX,
				pos.storage.tileY,
				(x, y, previous) -> previous == null ? pos.storage.estimateCanMoveTo(worker) : previous
		);
	}

	private static int getDestinationAmount(HaulFromLevelJob job, HaulFromLevelJob.HaulPosition destination) {
		int capacity = destination.storage.canAddFutureDropOff(job.item);
		int demand = SettlementStockSystem.getRemainingStockDemand(destination, job.item);
		return Math.max(0, Math.min(destination.amount, Math.min(capacity, demand)));
	}

	private static int getReservableAmount(
			HaulFromLevelJob job,
			HaulFromLevelJob.HaulPosition destination,
			DestinationCapacity destinationCapacity
	) {
		int globalReservedCapacity = destination.storage.canAddFutureDropOff(job.item);
		int batchCapacity = destinationCapacity.getCanPlanAmount(job.item);
		int amount = Math.min(
				Math.min(globalReservedCapacity, batchCapacity),
				Math.min(getDestinationAmount(job, destination), job.item.itemStackSize())
		);

		if (amount <= 0) {
			if (Logging.logEnabled) Logging.logMessage("[BatchedHauling] Destination full for item=" + job.item.item.getStringID()
					+ " at=" + destination.storage.tileX + "," + destination.storage.tileY
					+ " globalCapacity=" + globalReservedCapacity + " batchCapacity=" + batchCapacity);
		}
		return amount;
	}

	private static int addJobToSequence(
			LinkedListJobSequence sequence,
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			HaulFromLevelJob job,
			LevelStorage destination,
			LinkedList<SettlementStoragePickupSlot> slots
	) {
		int reserved = 0;
		ArrayList<PickupSettlementStorageActiveJob> pickups = new ArrayList<>();
		ArrayList<DropOffSettlementStorageActiveJob> dropOffs = new ArrayList<>();

		for (SettlementStoragePickupSlot slot : slots) {
			AtomicReference<InventoryItem> pickedUpItemRef = new AtomicReference<>();
			pickups.add(new PickupSettlementStorageActiveJob(worker, priority, slot, pickedUpItemRef));
			dropOffs.add(new DropOffSettlementStorageActiveJob(
					worker,
					priority,
					destination,
					(GameObjectReservable)null,
					false,
					() -> slot.isRemoved() ? pickedUpItemRef.get() : slot.item
			));
			reserved += slot.item.getAmount();
		}

		if (reserved <= 0) return 0;

		for (int i = pickups.size() - 1; i >= 0; i--) sequence.addFirst(pickups.get(i));
		for (DropOffSettlementStorageActiveJob dropOff : dropOffs) sequence.addLast(dropOff);
		sequence.addLast(new PerformLevelJobEventActiveJob(worker, priority, job));
		job.item.setAmount(Math.max(0, job.item.getAmount() - reserved));
		return reserved;
	}

	private static void removeSlots(LinkedList<SettlementStoragePickupSlot> slots) {
		if (slots == null) return;
		for (SettlementStoragePickupSlot slot : slots) {
			if (!slot.isRemoved()) slot.remove();
		}
	}

	private static final class DestinationCapacity {
		private final LevelStorage storage;
		private final InventoryRange simulatedRange;

		private DestinationCapacity(LevelStorage storage) {
			this.storage = storage;
			InventoryRange range = storage.getInventoryRange();
			this.simulatedRange = range == null
					? null
					: new InventoryRange(range.inventory.copy(), range.startSlot, range.endSlot);
		}

		private int getCanPlanAmount(InventoryItem item) {
			if (item == null || item.getAmount() <= 0 || simulatedRange == null) return 0;

			ItemCategoriesFilter filter = storage.getFilter();
			int filterAmount = filter == null
					? item.getAmount()
					: filter.getAddAmount(storage.level, item, simulatedRange, true);
			if (filterAmount <= 0) return 0;

			int inventoryAmount = simulatedRange.inventory.canAddItem(
					storage.level,
					(PlayerMob)null,
					item,
					simulatedRange.startSlot,
					simulatedRange.endSlot,
					"batchedhaul"
			);
			return Math.min(item.getAmount(), Math.min(filterAmount, inventoryAmount));
		}

		private void addPlanned(InventoryItem item) {
			if (item == null || item.getAmount() <= 0 || simulatedRange == null) return;
			InventoryItem copy = item.copy();
			simulatedRange.inventory.addItem(
					storage.level,
					(PlayerMob)null,
					copy,
					simulatedRange.startSlot,
					simulatedRange.endSlot,
					"batchedhaul",
					(InventoryAddConsumer)null
			);
		}
	}

	private static final class BatchCapacity {
		private final EntityJobWorker worker;
		private final List<InventoryItem> virtualItems = new ArrayList<>();
		private boolean virtualClayPackage;

		private BatchCapacity(EntityJobWorker worker) {
			this.worker = worker;
			for (Object value : worker.getWorkInventory().items()) {
				if (!(value instanceof InventoryItem)) continue;
				InventoryItem item = (InventoryItem)value;
				virtualItems.add(item.copy());
				if (ClayPackageSystem.isPackage(item)) virtualClayPackage = true;
			}
		}

		private boolean canPlan(InventoryItem item) {
			return getCanPlanAmount(item) > 0;
		}

		private int getCanPlanAmount(InventoryItem item) {
			if (item == null || item.getAmount() <= 0) return 0;

			if (supportsClayPackage() && ClayPackageSystem.isClayItem(item)) {
				if (virtualClayPackage) return item.getAmount();
				return virtualItems.size() < BuilderHumanMob.maxWorkInventoryStacks ? item.getAmount() : 0;
			}

			int maxByWorkerRules = getMaxByWorkerRules(item);
			if (maxByWorkerRules <= 0) return 0;

			for (InventoryItem existing : virtualItems) {
				if (!ClayPackageSystem.isPackage(existing)
						&& existing.canCombine(worker.getLevel(), null, item, "batchedhaul")) {
					int room = existing.itemStackSize() - existing.getAmount();
					if (room > 0) return Math.min(item.getAmount(), Math.min(room, maxByWorkerRules));
				}
			}

			if (virtualItems.size() >= vanillaWorkInventoryStacks) return 0;
			return Math.min(item.getAmount(), Math.min(item.itemStackSize(), maxByWorkerRules));
		}

		private int getMaxByWorkerRules(InventoryItem item) {
			if (worker.getMobWorker() instanceof BuilderHumanMob) return item.getAmount();

			int currentRuleAmount = worker.getWorkInventory().getCanAddAmount(item);
			if (currentRuleAmount <= 0) return 0;

			float brokerValue = 0.0F;
			for (InventoryItem existing : virtualItems) brokerValue += existing.getBrokerValue();
			float remainingValue = 300.0F - brokerValue;
			if (remainingValue < 0.0F) return 0;

			float singleItemBrokerValue = item.item.getBrokerValue(item);
			if (singleItemBrokerValue <= 0.0F) return Math.min(item.getAmount(), currentRuleAmount);
			int amountByBroker = (int)(remainingValue / singleItemBrokerValue);
			if (virtualItems.isEmpty()) amountByBroker = Math.max(amountByBroker, 1);
			return Math.min(item.getAmount(), Math.min(currentRuleAmount, amountByBroker));
		}

		private void addPlanned(InventoryItem item) {
			if (item == null || item.getAmount() <= 0) return;

			if (supportsClayPackage() && ClayPackageSystem.isClayItem(item)) {
				if (!virtualClayPackage) {
					virtualItems.add(ClayPackageSystem.newPackage());
					virtualClayPackage = true;
				}
				return;
			}

			int left = item.getAmount();
			for (InventoryItem existing : virtualItems) {
				if (ClayPackageSystem.isPackage(existing)) continue;
				if (!existing.canCombine(worker.getLevel(), null, item, "batchedhaul")) continue;
				int amount = Math.min(left, existing.itemStackSize() - existing.getAmount());
				if (amount <= 0) continue;
				existing.setAmount(existing.getAmount() + amount);
				left -= amount;
				if (left <= 0) return;
			}

			if (left > 0) virtualItems.add(item.copy(left));
		}

		private boolean supportsClayPackage() {
			return worker.getMobWorker() instanceof HumanMob;
		}
	}
}
