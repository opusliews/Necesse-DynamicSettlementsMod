package opusliews.crafting;

import necesse.engine.registries.ItemRegistry;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.DropOffSettlementStorageActiveJob;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.inventory.item.Item;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.levelData.jobs.HaulFromLevelJob;
import necesse.level.maps.levelData.settlementData.LevelStorage;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import necesse.level.maps.levelData.settlementData.SettlementStockInventoryAccess;
import opusliews.forge.MeltablePartSystem;
import opusliews.clay.ClayPackageSystem;
import opusliews.forge.ForgeCookingInput;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;
import opusliews.object.CraftingTaskBoardObjectEntity;
import opusliews.object.DynamicCraftingStationObjectEntity;
import opusliews.stock.SettlementStockSystem;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class CraftingAutoStockSystem {
	private static final int craftBatch = 3;
	private static final long updateIntervalMs = 500L;
	private static final long staleSweepIntervalMs = 5000L;
	private static final int autoStockPriority = 150;
	private static final AtomicLong nextRequestID = new AtomicLong(1L);
	private static final WeakHashMap<DynamicCraftingStationObjectEntity, Long> nextUpdateTimes = new WeakHashMap<>();
	private static final WeakHashMap<DynamicCraftingStationObjectEntity, LinkedHashMap<String, BatchRequest>> activeRequests = new WeakHashMap<>();
	private static final WeakHashMap<DynamicCraftingStationObjectEntity, HashSet<Long>> capacityBlockedTaskIDs = new WeakHashMap<>();
	private static final HashMap<Long, BatchRequest> requestsByID = new HashMap<>();
	private static final WeakHashMap<DropOffSettlementStorageActiveJob, DropOffTracking> trackedDropOffs = new WeakHashMap<>();
	private static long nextStaleSweepTime;

	private CraftingAutoStockSystem() {
	}

	public static void tickStation(DynamicCraftingStationObjectEntity station) {
		if (station == null || !station.getLevel().isServer()) return;
		sweepStaleRequests();
		// Settler crafting jobs are explicitly unavailable at night. Keep stale-request
		// housekeeping alive, but do not repeatedly re-plan ingredient deliveries that
		// no worker can act on. Because level time advances through the night, the first
		// daytime tick naturally falls past the old nextUpdateTimes value and refreshes
		// immediately.
		if (station.getLevel().getWorldEntity().isNight()) return;
		long now = station.getLevel().getTime();
		long next = nextUpdateTimes.getOrDefault(station, 0L);
		if (now < next) return;
		nextUpdateTimes.put(station, now + updateIntervalMs);
		refresh(station);
	}

	private static synchronized void sweepStaleRequests() {
		long now = System.currentTimeMillis();
		if (now < nextStaleSweepTime) return;
		nextStaleSweepTime = now + staleSweepIntervalMs;
		for (BatchRequest request : new ArrayList<>(requestsByID.values())) {
			if (!isRequestValid(request)) cancelRequest(request);
		}
		trackedDropOffs.entrySet().removeIf(entry -> {
			DropOffTracking tracking = entry.getValue();
			return tracking == null || !requestsByID.containsKey(tracking.requestID);
		});
	}

	public static synchronized boolean isInputCapacityBlocked(DynamicCraftingStationObjectEntity station) {
		HashSet<Long> blocked = capacityBlockedTaskIDs.get(station);
		return blocked != null && !blocked.isEmpty();
	}

	public static synchronized boolean isInputCapacityBlocked(DynamicCraftingStationObjectEntity station, long taskID) {
		HashSet<Long> blocked = capacityBlockedTaskIDs.get(station);
		return blocked != null && blocked.contains(taskID);
	}

	public static synchronized boolean isTaskWaitingDelivery(CraftingTaskBoardObjectEntity board, long taskID) {
		if (board == null || taskID <= 0L) return false;
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null) return false;
		LinkedHashMap<String, BatchRequest> stationRequests = activeRequests.get(station);
		if (stationRequests == null) return false;
		for (BatchRequest request : new ArrayList<>(stationRequests.values())) {
			if (!isRequestValid(request)) {
				cancelRequest(request);
				continue;
			}
			if (request.waitingTaskIDs.contains(taskID)) return true;
		}
		return false;
	}

	public static synchronized boolean isTaskDeliveryInProgress(CraftingTaskBoardObjectEntity board, long taskID) {
		if (board == null || taskID <= 0L) return false;
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null || !station.isAutoStockMissingIngredients()) return false;
		LinkedHashMap<String, BatchRequest> stationRequests = activeRequests.get(station);
		if (stationRequests == null) {
			if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockStatus] Board status check board="
					+ board.tileX + "," + board.tileY + " taskID=" + taskID + " activeRequests=0 result=false");
			return false;
		}
		for (BatchRequest request : new ArrayList<>(stationRequests.values())) {
			if (!isRequestValid(request)) {
				cancelRequest(request);
				continue;
			}
			if (request.deliveryStarted && request.waitingTaskIDs.contains(taskID)) {
				if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockStatus] Board status check board="
						+ board.tileX + "," + board.tileY + " taskID=" + taskID + " request=" + request.requestID + " result=true");
				return true;
			}
		}
		if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockStatus] Board status check board="
				+ board.tileX + "," + board.tileY + " taskID=" + taskID + " activeRequests=" + stationRequests.size() + " result=false");
		return false;
	}

	public static synchronized void markHaulJobClaimed(HaulFromLevelJob job, EntityJobWorker worker) {
		if (job == null) return;
		for (Object value : job.dropOffPositions) {
			if (!(value instanceof AutoStockHaulPosition)) continue;
			AutoStockHaulPosition position = (AutoStockHaulPosition)value;
			BatchRequest request = requestsByID.get(position.requestID);
			if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockStatus] Claim hook request=" + position.requestID
					+ " worker=" + (worker == null || worker.getMobWorker() == null ? "null" : worker.getMobWorker().getUniqueID())
					+ " jobRemoved=" + job.isRemoved() + " jobValid=" + job.isValid());
			if (!isRequestValid(request)) {
				if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockStatus] Claim rejected invalid request=" + position.requestID
						+ " jobRemoved=" + job.isRemoved() + " jobValid=" + job.isValid());
				if (request != null) cancelRequest(request);
				continue;
			}
			if (!request.deliveryStarted) {
				request.deliveryStarted = true;
				if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockStatus] Delivery started request=" + request.requestID
						+ " station=" + request.station.tileX + "," + request.station.tileY
						+ " board=" + request.board.tileX + "," + request.board.tileY
						+ " ingredient=" + request.ingredientKey
						+ (worker == null || worker.getMobWorker() == null ? "" : " worker=" + worker.getMobWorker().getUniqueID()));
				request.board.refreshTaskStatusesNow();
				if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockStatus] Forced board status refresh board="
						+ request.board.tileX + "," + request.board.tileY + " request=" + request.requestID);
			}
			return;
		}
	}

	public static synchronized void removeStation(DynamicCraftingStationObjectEntity station) {
		if (station == null) return;
		cancelRequests(station);
		nextUpdateTimes.remove(station);
		capacityBlockedTaskIDs.remove(station);
	}

	public static synchronized void onStationLinksChanged(DynamicCraftingStationObjectEntity station) {
		if (station == null) return;
		cancelRequests(station);
		capacityBlockedTaskIDs.remove(station);
		nextUpdateTimes.put(station, 0L);
	}

	public static boolean isAutoStockDestination(HaulFromLevelJob.HaulPosition position) {
		return position instanceof AutoStockHaulPosition;
	}

	public static synchronized boolean isValidAutoStockDestination(HaulFromLevelJob.HaulPosition position) {
		if (!(position instanceof AutoStockHaulPosition)) return false;
		AutoStockHaulPosition autoPosition = (AutoStockHaulPosition)position;
		BatchRequest request = requestsByID.get(autoPosition.requestID);
		if (!isRequestValid(request) || !isCurrentInputDestination(request.station, autoPosition.storage)) {
			if (request != null) cancelRequest(request);
			return false;
		}
		return true;
	}


	public static synchronized boolean removeIfInvalidAutoStockJob(HaulFromLevelJob job) {
		if (job == null) return false;
		boolean hasAutoStockDestination = false;
		for (Object value : job.dropOffPositions) {
			if (!(value instanceof AutoStockHaulPosition)) continue;
			hasAutoStockDestination = true;
			if (isValidAutoStockDestination((HaulFromLevelJob.HaulPosition)value)) return false;
		}
		if (!hasAutoStockDestination) return false;
		if (!job.isRemoved()) job.remove();
		return true;
	}

	public static synchronized int getRemainingDeliveryDemand(HaulFromLevelJob.HaulPosition position, InventoryItem item) {
		if (!(position instanceof AutoStockHaulPosition) || item == null || item.getAmount() <= 0) return 0;
		AutoStockHaulPosition autoPosition = (AutoStockHaulPosition)position;
		BatchRequest request = requestsByID.get(autoPosition.requestID);
		if (!isRequestValid(request) || !isCurrentInputDestination(request.station, autoPosition.storage)) {
			if (request != null) cancelRequest(request);
			return 0;
		}
		int remaining = Math.max(0, request.requestedAmount - request.deliveredAmount - request.inFlightAmount);
		return Math.min(item.getAmount(), Math.min(autoPosition.amount, remaining));
	}

	public static synchronized boolean registerDropOff(
			DropOffSettlementStorageActiveJob activeJob,
			HaulFromLevelJob.HaulPosition position,
			int amount
	) {
		if (activeJob == null || !(position instanceof AutoStockHaulPosition) || amount <= 0) return true;
		AutoStockHaulPosition autoPosition = (AutoStockHaulPosition)position;
		BatchRequest request = requestsByID.get(autoPosition.requestID);
		if (!isRequestValid(request) || !isCurrentInputDestination(request.station, autoPosition.storage)) {
			if (request != null) cancelRequest(request);
			return false;
		}
		int remaining = Math.max(0, request.requestedAmount - request.deliveredAmount - request.inFlightAmount);
		if (amount > remaining) return false;
		InventoryItem trackedItem = activeJob.dropOff == null ? null : activeJob.dropOff.getItem();
		if (trackedItem == null) return false;
		request.inFlightAmount += amount;
		trackedDropOffs.put(activeJob, new DropOffTracking(autoPosition.requestID, amount, trackedItem.item.getID()));
		return true;
	}

	public static synchronized int captureDropOffItemCount(DropOffSettlementStorageActiveJob job) {
		if (job == null || !trackedDropOffs.containsKey(job) || job.dropOff == null || job.dropOff.storage == null) return -1;
		InventoryItem item = job.dropOff.getItem();
		InventoryRange range = job.dropOff.storage.getInventoryRange();
		if (item == null || range == null) return -1;
		return countItem(range, item.item.getID());
	}

	public static synchronized void finishTrackedDropOff(
			DropOffSettlementStorageActiveJob job,
			ActiveJobResult result,
			int beforeCount
	) {
		DropOffTracking tracking = trackedDropOffs.remove(job);
		if (tracking == null) return;
		BatchRequest request = requestsByID.get(tracking.requestID);
		if (request == null) return;

		int delivered = 0;
		if (result == ActiveJobResult.FINISHED && beforeCount >= 0 && job.dropOff != null && job.dropOff.storage != null) {
			InventoryRange range = job.dropOff.storage.getInventoryRange();
			if (range != null) delivered = Math.max(0, countItem(range, tracking.itemID) - beforeCount);
		}
		request.inFlightAmount = Math.max(0, request.inFlightAmount - tracking.expectedAmount);
		request.deliveredAmount = Math.min(request.requestedAmount, request.deliveredAmount + delivered);
		if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockDebug] DROPOFF_FINISH request=" + tracking.requestID
				+ " expected=" + tracking.expectedAmount + " delivered=" + delivered
				+ " totalDelivered=" + request.deliveredAmount + "/" + request.requestedAmount
				+ " result=" + result);
		if (request.deliveredAmount >= request.requestedAmount) completeRequest(request);
	}

	public static synchronized void cancelTrackedDropOff(DropOffSettlementStorageActiveJob job) {
		DropOffTracking tracking = trackedDropOffs.remove(job);
		if (tracking == null) return;
		BatchRequest request = requestsByID.get(tracking.requestID);
		if (request != null) request.inFlightAmount = Math.max(0, request.inFlightAmount - tracking.expectedAmount);
	}

	public static synchronized boolean isTrackedDropOff(DropOffSettlementStorageActiveJob job) {
		return job != null && trackedDropOffs.containsKey(job);
	}

	public static int getUnfilteredFutureDropOffCapacity(LevelStorage storage, InventoryItem item) {
		if (storage == null || storage.level == null || item == null || item.getAmount() <= 0) return 0;
		InventoryRange range = SettlementStockInventoryAccess.copyFutureDropOffRange(storage);
		return getUnfilteredCapacity(storage, range, item);
	}

	public static int getUnfilteredDropOffCapacity(LevelStorage storage, InventoryItem item) {
		if (storage == null || storage.level == null || item == null || item.getAmount() <= 0) return 0;
		return getUnfilteredCapacity(storage, storage.getInventoryRange(), item);
	}

	private static int getUnfilteredCapacity(LevelStorage storage, InventoryRange range, InventoryItem item) {
		if (range == null) return 0;
		return Math.max(0, range.inventory.canAddItem(
				storage.level, null, item, range.startSlot, range.endSlot, "craftingautostock"
		));
	}

	public static boolean isTrackedDropOffValid(DropOffSettlementStorageActiveJob job) {
		if (!isTrackedDropOff(job) || job.dropOff == null || job.dropOff.storage == null) return false;
		if (!job.dropOff.storage.isStorageValid()) return false;
		InventoryItem item = job.dropOff.getItem();
		return item != null && getUnfilteredDropOffCapacity(job.dropOff.storage, item) > 0;
	}

	public static ActiveJobResult tryPerformTrackedDropOff(DropOffSettlementStorageActiveJob job, EntityJobWorker worker) {
		if (!isTrackedDropOff(job)) return null;
		if (job == null || worker == null || job.dropOff == null || job.dropOff.storage == null) return ActiveJobResult.FAILED;
		if (worker.isInWorkAnimation()) return ActiveJobResult.PERFORMING;

		worker.clearHoldAnimation();
		InventoryItem dropOffItem = job.dropOff.getItem();
		InventoryRange range = job.dropOff.storage.getInventoryRange();
		if (dropOffItem == null || range == null) return ActiveJobResult.FAILED;

		ListIterator<InventoryItem> iterator = worker.getWorkInventory().listIterator();
		while (iterator.hasNext()) {
			InventoryItem carried = iterator.next();

			if (ClayPackageSystem.isPackage(carried)) {
				InventoryItem packaged = null;
				for (InventoryItem stored : ClayPackageSystem.getContents(carried)) {
					if (stored.equals(job.getLevel(), dropOffItem, true, false, "claypackage")) {
						packaged = stored;
						break;
					}
				}
				if (packaged == null) continue;

				int requested = Math.min(packaged.getAmount(), dropOffItem.getAmount());
				if (requested <= 0) continue;
				InventoryItem toAdd = packaged.copy(requested);
				range.inventory.addItem(
						job.dropOff.storage.level, null, toAdd,
						range.startSlot, range.endSlot, "craftingautostock"
				);
				int added = requested - toAdd.getAmount();
				if (added <= 0) return ActiveJobResult.FAILED;

				ClayPackageSystem.removeMatching(carried, packaged, added, worker.getMobWorker().getLevel());
				if (ClayPackageSystem.isEmpty(carried)) iterator.remove();
				worker.getWorkInventory().markDirty();
				worker.showPlaceAnimation(job.tileX * 32 + 16, job.tileY * 32 + 16, packaged.item, 250, true);
				if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockDebug] DROPOFF_PACKAGE item="
						+ packaged.item.getStringID() + " amount=" + added + " destination="
						+ job.dropOff.storage.level.getIdentifier() + "@" + job.dropOff.storage.tileX + "," + job.dropOff.storage.tileY);
				job.dropOff.remove();
				return ActiveJobResult.FINISHED;
			}

			if (!carried.equals(job.getLevel(), dropOffItem, true, false, "equals")) continue;

			int requested = Math.min(carried.getAmount(), dropOffItem.getAmount());
			if (requested <= 0) continue;
			InventoryItem toAdd = carried.copy(requested);
			range.inventory.addItem(
					job.dropOff.storage.level, null, toAdd,
					range.startSlot, range.endSlot, "craftingautostock"
			);
			int added = requested - toAdd.getAmount();
			if (added <= 0) return ActiveJobResult.FAILED;

			Item placedItem = carried.item;
			carried.setAmount(carried.getAmount() - added);
			if (carried.getAmount() <= 0) iterator.remove();
			worker.getWorkInventory().markDirty();
			worker.showPlaceAnimation(job.tileX * 32 + 16, job.tileY * 32 + 16, placedItem, 250, true);
			if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockDebug] DROPOFF_RAW item="
					+ placedItem.getStringID() + " amount=" + added + " destination="
					+ job.dropOff.storage.level.getIdentifier() + "@" + job.dropOff.storage.tileX + "," + job.dropOff.storage.tileY);
			job.dropOff.remove();
			return ActiveJobResult.FINISHED;
		}

		if (Logging.logEnabled) Logging.logMessage("[CraftingAutoStockDebug] DROPOFF_FAILED reason=item-not-carried item="
				+ dropOffItem.item.getStringID() + " worker=" + worker.getMobWorker().getUniqueID());
		return ActiveJobResult.FAILED;
	}

	private static void refresh(DynamicCraftingStationObjectEntity station) {
		SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomainQuiet(
				station.getLevel().getServer(),
				station.getLevel().getIdentifier(),
				station.tileX,
				station.tileY
		);
		ServerSettlementData settlement = domain == null ? null : domain.getSettlement();
		ArrayList<SettlementInventory> inputs = getInputStorages(station, settlement);
		Point boardPoint = station.getTaskBoard();

		if (!station.isAutoStockMissingIngredients()) {
			removeStation(station);
			return;
		}
		if (settlement == null) {
			removeStation(station);
			return;
		}
		if (inputs.isEmpty()) {
			removeStation(station);
			return;
		}

		if (boardPoint == null || !(station.getLevel().entityManager.getObjectEntity(boardPoint.x, boardPoint.y) instanceof CraftingTaskBoardObjectEntity)) {
			removeStation(station);
			return;
		}
		CraftingTaskBoardObjectEntity board = (CraftingTaskBoardObjectEntity)station.getLevel().entityManager.getObjectEntity(boardPoint.x, boardPoint.y);

		LinkedHashMap<String, BatchRequest> stationRequests;
		synchronized (CraftingAutoStockSystem.class) {
			stationRequests = activeRequests.computeIfAbsent(station, ignored -> new LinkedHashMap<>());
			for (BatchRequest request : new ArrayList<>(stationRequests.values())) {
				if (!isRequestValid(request)) cancelRequest(request);
			}
			stationRequests = activeRequests.computeIfAbsent(station, ignored -> new LinkedHashMap<>());
			capacityBlockedTaskIDs.put(station, new HashSet<>());
		}

		PlanningState planning = new PlanningState(settlement, inputs, stationRequests.values());
		for (BatchRequest request : new ArrayList<>(stationRequests.values())) {
			if (request.deliveredAmount >= request.requestedAmount) {
				completeRequest(request);
				continue;
			}
			scheduleRemaining(request, planning);
		}

		ArrayList<Demand> demands = collectNewBatchDemands(board, inputs, stationRequests);

		for (Demand demand : demands) {
			PlanningState attempt = planning.copy();
			PlanResult plan = planDemand(attempt, inputs, demand);
			if (!plan.success()) {
				if (plan.failure == PlanFailure.INSUFFICIENT_DESTINATION) {
					synchronized (CraftingAutoStockSystem.class) {
						capacityBlockedTaskIDs.get(station).addAll(demand.taskIDs);
					}
				}
				continue;
			}
			ArrayList<PlannedDelivery> deliveries = plan.deliveries;

			long requestID = allocateRequestID();
			BatchRequest request = new BatchRequest(requestID, station, board, demand.key, demand.amount, demand.taskIDs);
			synchronized (CraftingAutoStockSystem.class) {
				stationRequests.put(demand.key, request);
				requestsByID.put(requestID, request);
			}
			planning = attempt;
			scheduleDeliveries(request, deliveries);
		}
	}

	private static ArrayList<Demand> collectNewBatchDemands(
			CraftingTaskBoardObjectEntity board,
			List<SettlementInventory> inputs,
			Map<String, BatchRequest> existing
	) {
		LinkedHashMap<String, Demand> merged = new LinkedHashMap<>();
		for (CraftingTask task : board.getTasks()) {
			if (task == null) continue;
			if (task.paused || task.amount <= 0 || task.taskID <= 0L) {
				continue;
			}
			CraftingTaskRecipe taskRecipe = CraftingTaskLogic.getTaskRecipe(board, task);
			if (taskRecipe == null) {
				continue;
			}
			List<String> missing = CraftingTaskLogic.getMissingIngredients(board, taskRecipe);
			if (missing.isEmpty()) continue;

			if (taskRecipe.type == CraftingTaskRecipe.Type.CUSTOM_FORGE) {
				addMissingForgeInputDemand(merged, existing, inputs, task.taskID, taskRecipe.forgeRecipe.firstInput);
				addMissingForgeInputDemand(merged, existing, inputs, task.taskID, taskRecipe.forgeRecipe.secondInput);
			} else if (taskRecipe.recipe != null) {
				addMissingRecipeDemands(merged, existing, inputs, task.taskID, taskRecipe.recipe);
			}
		}
		return new ArrayList<>(merged.values());
	}

	private static void addMissingRecipeDemands(
			Map<String, Demand> merged,
			Map<String, BatchRequest> existing,
			List<SettlementInventory> inputs,
			long taskID,
			Recipe recipe
	) {
		ArrayList<InventoryRange> simulated = copyCurrentRanges(inputs);
		if (inputs.isEmpty()) return;
		necesse.level.maps.Level level = inputs.get(0).level;
		for (Ingredient ingredient : recipe.ingredients) {
			int perCraft = ingredient.getIngredientAmount();
			boolean missing;
			if (perCraft <= 0) {
				missing = true;
				for (InventoryRange range : simulated) {
					if (ingredient.hasIngredientRange(level, null, range)) {
						missing = false;
						break;
					}
				}
			} else {
				int remaining = perCraft;
				for (InventoryRange range : simulated) {
					if (remaining <= 0) break;
					remaining -= range.inventory.removeItems(level, null, ingredient, null, remaining, range.startSlot, range.endSlot, null);
				}
				missing = remaining > 0;
			}
			if (!missing) continue;

			String key = ingredient.isGlobalIngredient()
					? "ingredient:" + ingredient.ingredientStringID
					: "exact:" + ingredient.ingredientStringID;
			if (existing.containsKey(key)) {
				existing.get(key).waitingTaskIDs.add(taskID);
				continue;
			}
			int amount = perCraft <= 0 ? 1 : perCraft * craftBatch;
			if (ingredient.isGlobalIngredient()) addDemand(merged, Demand.forIngredient(key, ingredient, amount, taskID));
			else addDemand(merged, Demand.forExact(key, ingredient.ingredientStringID, amount, taskID));
		}
	}

	private static void addMissingForgeInputDemand(
			Map<String, Demand> merged,
			Map<String, BatchRequest> existing,
			List<SettlementInventory> inputs,
			long taskID,
			ForgeCookingInput input
	) {
		if (input == null || countExact(inputs, input.itemStringID) >= input.amount) return;
		String key = "exact:" + input.itemStringID;
		if (existing.containsKey(key)) {
			existing.get(key).waitingTaskIDs.add(taskID);
			return;
		}
		boolean reusable = input.resultBehavior == ForgeCookingInput.ResultBehavior.KEEP
				|| input.resultBehavior == ForgeCookingInput.ResultBehavior.DURABILITY_USE;
		int amount = Math.max(1, input.amount) * (reusable ? 1 : craftBatch);
		addDemand(merged, Demand.forExact(key, input.itemStringID, amount, taskID));
	}

	private static void addDemand(Map<String, Demand> merged, Demand demand) {
		Demand current = merged.get(demand.key);
		if (current == null) {
			merged.put(demand.key, demand);
		} else {
			current.amount += demand.amount;
			current.taskIDs.addAll(demand.taskIDs);
		}
	}

	private static PlanResult planDemand(
			PlanningState state,
			List<SettlementInventory> inputs,
			Demand demand
	) {
		ArrayList<SourceCandidate> candidates = getSourceCandidates(state, inputs, demand);
		int sourceAvailable = candidates.stream().mapToInt(candidate -> candidate.available).sum();
		if (sourceAvailable < demand.amount) return PlanResult.failure(PlanFailure.INSUFFICIENT_SOURCE);

		ArrayList<PlannedDelivery> deliveries = new ArrayList<>();
		int remaining = demand.amount;
		for (SourceCandidate candidate : candidates) {
			if (remaining <= 0) break;
			int candidateRemaining = Math.min(remaining, candidate.available);
			while (candidateRemaining > 0) {
				Destination destination = findBestDestination(inputs, state.destinationRanges, candidate.item, candidateRemaining);
				if (destination == null) return PlanResult.failure(PlanFailure.INSUFFICIENT_DESTINATION);
				int amount = Math.min(candidateRemaining, destination.capacity);
				if (amount <= 0) return PlanResult.failure(PlanFailure.INSUFFICIENT_DESTINATION);
				addToSimulatedDestination(destination, candidate.item, amount);
				state.reserveSource(candidate.storage, candidate.item.getID(), amount);
				deliveries.add(new PlannedDelivery(candidate.storage, destination.storage, candidate.item, amount));
				candidateRemaining -= amount;
				remaining -= amount;
			}
		}
		return remaining == 0
				? PlanResult.success(deliveries)
				: PlanResult.failure(PlanFailure.INSUFFICIENT_DESTINATION);
	}

	private static ArrayList<SourceCandidate> getSourceCandidates(
			PlanningState state,
			List<SettlementInventory> inputs,
			Demand demand
	) {
		ArrayList<SourceCandidate> result = new ArrayList<>();
		for (SettlementInventory storage : SettlementLevelStorageManager.getStorage(state.settlement)) {
			if (storage == null || storage.level == null || !storage.isStorageValid() || inputs.contains(storage) || storage.isAllAdjacentSolid()) continue;
			InventoryRange range = storage.getInventoryRange();
			if (range == null) continue;
			HashMap<Integer, Integer> counts = new HashMap<>();
			for (int slot = range.startSlot; slot <= range.endSlot; slot++) {
				InventoryItem stack = range.inventory.getItem(slot);
				if (stack != null && demand.matches(stack)) counts.merge(stack.item.getID(), stack.getAmount(), Integer::sum);
			}
			for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
				Item item = ItemRegistry.getItem(entry.getKey());
				if (item == null) continue;
				int removable = SettlementStockSystem.getMaxAutoStockRemovable(storage, new InventoryItem(item, entry.getValue()));
				int available = Math.max(0, removable - state.getReservedSource(storage, item.getID()));
				if (available > 0) result.add(new SourceCandidate(storage, item, available));
			}
		}
		result.sort(Comparator.comparingInt((SourceCandidate candidate) -> -candidate.available)
				.thenComparing(candidate -> candidate.item.getStringID())
				.thenComparingInt(candidate -> candidate.storage.tileY)
				.thenComparingInt(candidate -> candidate.storage.tileX));
		return result;
	}

	private static Destination findBestDestination(
			List<SettlementInventory> inputs,
			IdentityHashMap<SettlementInventory, InventoryRange> simulated,
			Item item,
			int amount
	) {
		ArrayList<Destination> destinations = new ArrayList<>();
		for (SettlementInventory input : inputs) {
			InventoryRange range = simulated.get(input);
			if (range == null) continue;
			InventoryItem probe = new InventoryItem(item, amount);
			int inventoryAmount = range.inventory.canAddItem(input.level, null, probe, range.startSlot, range.endSlot, "craftingautostock");
			int capacity = Math.max(0, Math.min(amount, inventoryAmount));
			if (capacity <= 0) continue;
			destinations.add(new Destination(input, range, capacity, countItem(range, item.getID()) > 0));
		}
		destinations.sort(Comparator.comparing((Destination destination) -> !destination.hasItem)
				.thenComparingInt(destination -> -destination.capacity)
				.thenComparingInt(destination -> destination.storage.tileY)
				.thenComparingInt(destination -> destination.storage.tileX));
		return destinations.isEmpty() ? null : destinations.get(0);
	}

	private static void addToSimulatedDestination(Destination destination, Item item, int amount) {
		InventoryItem stack = new InventoryItem(item, amount);
		destination.range.inventory.addItem(
				destination.storage.level, null, stack,
				destination.range.startSlot, destination.range.endSlot,
				"craftingautostock"
		);
	}

	private static void scheduleRemaining(BatchRequest request, PlanningState planning) {
		if (!isRequestValid(request)) {
			if (request != null) cancelRequest(request);
			return;
		}
		int queued = request.getQueuedAmount();
		int needed = Math.max(0, request.requestedAmount - request.deliveredAmount - request.inFlightAmount - queued);
		if (needed <= 0) return;

		Demand demand = request.toDemand(needed);
		PlanningState attempt = planning.copy();
		PlanResult plan = planDemand(attempt, planning.inputs, demand);
		if (!plan.success()) {
			if (plan.failure == PlanFailure.INSUFFICIENT_DESTINATION) {
				synchronized (CraftingAutoStockSystem.class) {
					HashSet<Long> blocked = capacityBlockedTaskIDs.computeIfAbsent(request.station, ignored -> new HashSet<>());
					blocked.addAll(request.waitingTaskIDs);
				}
			}
			return;
		}
		planning.copyFrom(attempt);
		scheduleDeliveries(request, plan.deliveries);
	}

	private static void scheduleDeliveries(BatchRequest request, List<PlannedDelivery> deliveries) {
		for (PlannedDelivery delivery : deliveries) {
			HaulFromLevelJob job = new HaulFromLevelJob(delivery.source, new InventoryItem(delivery.item, delivery.amount));
			job.onlyAcceptSpecificAmount = false;
			job.dropOffPositions.add(new AutoStockHaulPosition(delivery.destination, autoStockPriority, delivery.amount, request.requestID));
			HaulFromLevelJob added = (HaulFromLevelJob)delivery.source.level.jobsLayer.addJob(job, false, true);

			if (added != null) request.jobs.add(added);
		}
	}

	private static synchronized void completeRequest(BatchRequest request) {
		cancelRequest(request);
	}

	private static synchronized void cancelRequest(BatchRequest request) {
		if (request == null) return;
		requestsByID.remove(request.requestID);
		LinkedHashMap<String, BatchRequest> stationRequests = activeRequests.get(request.station);
		if (stationRequests != null && stationRequests.get(request.ingredientKey) == request) {
			stationRequests.remove(request.ingredientKey);
			if (stationRequests.isEmpty()) activeRequests.remove(request.station);
		}
		trackedDropOffs.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue().requestID == request.requestID);
		for (HaulFromLevelJob job : request.jobs) {
			if (job != null && !job.isRemoved()) job.remove();
		}
		request.jobs.clear();
		request.inFlightAmount = 0;
	}

	private static synchronized void cancelRequests(DynamicCraftingStationObjectEntity station) {
		LinkedHashMap<String, BatchRequest> stationRequests = activeRequests.get(station);
		if (stationRequests == null) return;
		for (BatchRequest request : new ArrayList<>(stationRequests.values())) cancelRequest(request);
		activeRequests.remove(station);
	}

	private static boolean isRequestValid(BatchRequest request) {
		if (request == null || request.station == null || request.board == null) return false;
		if (requestsByID.get(request.requestID) != request) return false;
		DynamicCraftingStationObjectEntity station = request.station;
		if (!station.getLevel().isServer() || !station.isAutoStockMissingIngredients()) return false;
		if (station.getLevel().entityManager.getObjectEntity(station.tileX, station.tileY) != station) return false;
		Point boardPoint = station.getTaskBoard();
		if (boardPoint == null || boardPoint.x != request.board.tileX || boardPoint.y != request.board.tileY) return false;
		if (station.getLevel().entityManager.getObjectEntity(boardPoint.x, boardPoint.y) != request.board) return false;
		if (request.board.getLinkedStationEntity() != station) return false;
		for (CraftingTask task : request.board.getTasks()) {
			if (task != null && request.waitingTaskIDs.contains(task.taskID)) return true;
		}
		return false;
	}

	private static boolean isCurrentInputDestination(DynamicCraftingStationObjectEntity station, LevelStorage storage) {
		if (station == null || !(storage instanceof SettlementInventory)) return false;
		SettlementInventory settlementStorage = (SettlementInventory)storage;
		if (settlementStorage.level == null || !settlementStorage.isStorageValid()) return false;
		if (!station.getLevel().getIdentifier().equals(settlementStorage.level.getIdentifier())) return false;
		for (Point point : station.getInputStorages()) {
			if (point.x == settlementStorage.tileX && point.y == settlementStorage.tileY) return true;
		}
		return false;
	}

	private static long allocateRequestID() {
		long id = nextRequestID.getAndIncrement();
		if (id > 0L) return id;
		synchronized (CraftingAutoStockSystem.class) {
			nextRequestID.set(2L);
			return 1L;
		}
	}

	private static ArrayList<SettlementInventory> getInputStorages(
			DynamicCraftingStationObjectEntity station,
			ServerSettlementData settlement
	) {
		ArrayList<SettlementInventory> result = new ArrayList<>();
		if (settlement == null) return result;
		for (Point point : station.getInputStorages()) {
			SettlementInventory storage = SettlementLevelStorageManager.getStorage(
					settlement, station.getLevel().getIdentifier(), point.x, point.y
			);
			if (storage != null && storage.isStorageValid()) result.add(storage);
		}
		return result;
	}

	private static ArrayList<InventoryRange> copyCurrentRanges(List<SettlementInventory> inputs) {
		ArrayList<InventoryRange> result = new ArrayList<>();
		for (SettlementInventory input : inputs) {
			InventoryRange range = input.getInventoryRange();
			if (range != null) result.add(new InventoryRange(range.inventory.copy(), range.startSlot, range.endSlot));
		}
		return result;
	}

	private static int countExact(List<SettlementInventory> inputs, String itemStringID) {
		if (itemStringID == null) return 0;
		int total = 0;
		for (SettlementInventory input : inputs) {
			InventoryRange range = input.getInventoryRange();
			if (range == null) continue;
			for (int slot = range.startSlot; slot <= range.endSlot; slot++) {
				InventoryItem item = range.inventory.getItem(slot);
				if (item != null && itemStringID.equals(item.item.getStringID())) total += item.getAmount();
			}
		}
		return total;
	}

	private static int countItem(InventoryRange range, int itemID) {
		if (range == null) return 0;
		int total = 0;
		for (int slot = range.startSlot; slot <= range.endSlot; slot++) {
			InventoryItem item = range.inventory.getItem(slot);
			if (item != null && item.item.getID() == itemID) total += item.getAmount();
		}
		return total;
	}

	public static final class AutoStockHaulPosition extends HaulFromLevelJob.HaulPosition {
		public final long requestID;

		public AutoStockHaulPosition(SettlementInventory storage, int priority, int amount, long requestID) {
			super(storage, priority, amount);
			this.requestID = requestID;
		}
	}

	private static final class BatchRequest {
		private final long requestID;
		private final DynamicCraftingStationObjectEntity station;
		private final CraftingTaskBoardObjectEntity board;
		private final String ingredientKey;
		private final int requestedAmount;
		private final HashSet<Long> waitingTaskIDs;
		private final ArrayList<HaulFromLevelJob> jobs = new ArrayList<>();
		private int deliveredAmount;
		private int inFlightAmount;
		private boolean deliveryStarted;

		private BatchRequest(long requestID, DynamicCraftingStationObjectEntity station, CraftingTaskBoardObjectEntity board, String ingredientKey, int requestedAmount, Set<Long> taskIDs) {
			this.requestID = requestID;
			this.station = station;
			this.board = board;
			this.ingredientKey = ingredientKey;
			this.requestedAmount = Math.max(1, requestedAmount);
			this.waitingTaskIDs = new HashSet<>(taskIDs);
		}

		private int getQueuedAmount() {
			int total = 0;
			jobs.removeIf(job -> job == null || job.isRemoved() || !job.isValid());
			for (HaulFromLevelJob job : jobs) {
				if (job.item != null && job.item.getAmount() > 0) total += job.item.getAmount();
			}
			return total;
		}

		private Demand toDemand(int amount) {
			if (ingredientKey.startsWith("ingredient:")) {
				Ingredient ingredient = new Ingredient(ingredientKey.substring("ingredient:".length()), 1);
				return new Demand(ingredientKey, ingredient, null, amount, waitingTaskIDs);
			}
			return new Demand(ingredientKey, null, ingredientKey.substring("exact:".length()), amount, waitingTaskIDs);
		}
	}

	private static final class DropOffTracking {
		private final long requestID;
		private final int expectedAmount;
		private final int itemID;

		private DropOffTracking(long requestID, int expectedAmount, int itemID) {
			this.requestID = requestID;
			this.expectedAmount = expectedAmount;
			this.itemID = itemID;
		}
	}

	private enum PlanFailure {
		NONE,
		INSUFFICIENT_SOURCE,
		INSUFFICIENT_DESTINATION
	}

	private static final class PlanResult {
		private final PlanFailure failure;
		private final ArrayList<PlannedDelivery> deliveries;

		private PlanResult(PlanFailure failure, ArrayList<PlannedDelivery> deliveries) {
			this.failure = failure;
			this.deliveries = deliveries;
		}

		private static PlanResult success(ArrayList<PlannedDelivery> deliveries) {
			return new PlanResult(PlanFailure.NONE, deliveries);
		}

		private static PlanResult failure(PlanFailure failure) {
			return new PlanResult(failure, new ArrayList<>());
		}

		private boolean success() {
			return failure == PlanFailure.NONE;
		}
	}

	private static final class Demand {
		private final String key;
		private final Ingredient ingredient;
		private final String exactItemStringID;
		private int amount;
		private final HashSet<Long> taskIDs;

		private Demand(String key, Ingredient ingredient, String exactItemStringID, int amount, Set<Long> taskIDs) {
			this.key = key;
			this.ingredient = ingredient;
			this.exactItemStringID = exactItemStringID;
			this.amount = Math.max(1, amount);
			this.taskIDs = new HashSet<>(taskIDs);
		}

		private static Demand forIngredient(String key, Ingredient ingredient, int amount, long taskID) {
			HashSet<Long> taskIDs = new HashSet<>();
			taskIDs.add(taskID);
			return new Demand(key, ingredient, null, amount, taskIDs);
		}

		private static Demand forExact(String key, String itemStringID, int amount, long taskID) {
			HashSet<Long> taskIDs = new HashSet<>();
			taskIDs.add(taskID);
			return new Demand(key, null, itemStringID, amount, taskIDs);
		}

		private boolean matches(InventoryItem item) {
			if (item == null || item.item == null) return false;
			if (ingredient != null) {
				if (MeltablePartSystem.isPartiallyMelted(item)) return false;
				return ingredient.matchesItem(item.item);
			}
			return exactItemStringID.equals(item.item.getStringID());
		}
	}

	private static final class PlanningState {
		private final ServerSettlementData settlement;
		private final ArrayList<SettlementInventory> inputs;
		private final IdentityHashMap<SettlementInventory, InventoryRange> destinationRanges = new IdentityHashMap<>();
		private final IdentityHashMap<SettlementInventory, HashMap<Integer, Integer>> reservedSource = new IdentityHashMap<>();

		private PlanningState(ServerSettlementData settlement, List<SettlementInventory> inputs, Iterable<BatchRequest> requests) {
			this.settlement = settlement;
			this.inputs = new ArrayList<>(inputs);
			for (SettlementInventory input : inputs) {
				InventoryRange future = SettlementStockInventoryAccess.copyFutureDropOffRange(input);
				if (future == null) future = input.getInventoryRange();
				if (future != null) destinationRanges.put(input, new InventoryRange(future.inventory.copy(), future.startSlot, future.endSlot));
			}
			for (BatchRequest request : requests) {
				for (HaulFromLevelJob job : request.jobs) {
					if (job == null || job.isRemoved() || !job.isValid() || job.item == null || job.item.getAmount() <= 0) continue;
					reserveSource((SettlementInventory)job.storage, job.item.item.getID(), job.item.getAmount());
					for (Object value : job.dropOffPositions) {
						if (!(value instanceof AutoStockHaulPosition)) continue;
						AutoStockHaulPosition position = (AutoStockHaulPosition)value;
						InventoryRange range = destinationRanges.get(position.storage);
						if (range == null) continue;
						InventoryItem queued = job.item.copy(job.item.getAmount());
						range.inventory.addItem(position.storage.level, null, queued, range.startSlot, range.endSlot, "craftingautostock");
						break;
					}
				}
			}
		}

		private PlanningState(PlanningState other) {
			this.settlement = other.settlement;
			this.inputs = new ArrayList<>(other.inputs);
			for (Map.Entry<SettlementInventory, InventoryRange> entry : other.destinationRanges.entrySet()) {
				InventoryRange range = entry.getValue();
				destinationRanges.put(entry.getKey(), new InventoryRange(range.inventory.copy(), range.startSlot, range.endSlot));
			}
			for (Map.Entry<SettlementInventory, HashMap<Integer, Integer>> entry : other.reservedSource.entrySet()) {
				reservedSource.put(entry.getKey(), new HashMap<>(entry.getValue()));
			}
		}

		private PlanningState copy() {
			return new PlanningState(this);
		}

		private void copyFrom(PlanningState other) {
			destinationRanges.clear();
			for (Map.Entry<SettlementInventory, InventoryRange> entry : other.destinationRanges.entrySet()) {
				InventoryRange range = entry.getValue();
				destinationRanges.put(entry.getKey(), new InventoryRange(range.inventory.copy(), range.startSlot, range.endSlot));
			}
			reservedSource.clear();
			for (Map.Entry<SettlementInventory, HashMap<Integer, Integer>> entry : other.reservedSource.entrySet()) {
				reservedSource.put(entry.getKey(), new HashMap<>(entry.getValue()));
			}
		}

		private int getReservedSource(SettlementInventory storage, int itemID) {
			HashMap<Integer, Integer> values = reservedSource.get(storage);
			return values == null ? 0 : values.getOrDefault(itemID, 0);
		}

		private void reserveSource(SettlementInventory storage, int itemID, int amount) {
			if (storage == null || amount <= 0) return;
			reservedSource.computeIfAbsent(storage, ignored -> new HashMap<>()).merge(itemID, amount, Integer::sum);
		}
	}

	private static final class SourceCandidate {
		private final SettlementInventory storage;
		private final Item item;
		private final int available;

		private SourceCandidate(SettlementInventory storage, Item item, int available) {
			this.storage = storage;
			this.item = item;
			this.available = available;
		}
	}

	private static final class PlannedDelivery {
		private final SettlementInventory source;
		private final SettlementInventory destination;
		private final Item item;
		private final int amount;

		private PlannedDelivery(SettlementInventory source, SettlementInventory destination, Item item, int amount) {
			this.source = source;
			this.destination = destination;
			this.item = item;
			this.amount = amount;
		}
	}

	private static final class Destination {
		private final SettlementInventory storage;
		private final InventoryRange range;
		private final int capacity;
		private final boolean hasItem;

		private Destination(SettlementInventory storage, InventoryRange range, int capacity, boolean hasItem) {
			this.storage = storage;
			this.range = range;
			this.capacity = capacity;
			this.hasItem = hasItem;
		}
	}
}
