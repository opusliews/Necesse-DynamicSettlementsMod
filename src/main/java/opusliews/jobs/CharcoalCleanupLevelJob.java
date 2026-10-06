package opusliews.jobs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.entity.mobs.job.LinkedListJobSequence;
import necesse.entity.mobs.job.activeJob.ActiveJob;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.DropOffSettlementStorageActiveJob;
import necesse.entity.mobs.job.activeJob.PickupItemEntityActiveJob;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.entity.pickup.ItemPickupEntity;
import necesse.entity.pickup.ItemPickupReservedAmount;
import necesse.inventory.InventoryItem;
import necesse.level.maps.levelData.jobs.HasStorageLevelJob;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import opusliews.charcoal.CharcoalProductionZone;
import opusliews.clay.ClayPackageSystem;
import opusliews.logging.Logging;
import opusliews.network.PacketBuilderTilePlaceSound;
import opusliews.tile.ShallowHoleTile;
import opusliews.tile.ShallowHoleSystem;

public class CharcoalCleanupLevelJob extends TileLevelJob {
	private static final long holeFillTime = 2000L;
	private final ArrayList<Integer> trackedPickupUniqueIDs = new ArrayList<>();
	private final ArrayList<ItemPickupEntity> trackedPickups = new ArrayList<>();

	public CharcoalCleanupLevelJob(int tileX, int tileY, ItemPickupEntity charcoalPickup) {
		this(tileX, tileY, charcoalPickup == null ? null : java.util.Arrays.asList(charcoalPickup));
	}

	public CharcoalCleanupLevelJob(int tileX, int tileY, List<ItemPickupEntity> pickups) {
		this(tileX, tileY, pickups, getPickupUniqueIDs(pickups));
	}

	public CharcoalCleanupLevelJob(int tileX, int tileY, List<ItemPickupEntity> pickups, List<Integer> pickupUniqueIDs) {
		super(tileX, tileY);
		if (pickupUniqueIDs != null) this.trackedPickupUniqueIDs.addAll(pickupUniqueIDs);
		setTrackedPickups(pickups);
	}

	public CharcoalCleanupLevelJob(LoadData save) {
		super(save);
		for (LoadData pickupSave : save.getLoadDataByName("PICKUP")) {
			int pickupUniqueID = pickupSave.getInt("pickupUniqueID", 0, false);
			if (pickupUniqueID != 0) trackedPickupUniqueIDs.add(pickupUniqueID);
		}
		if (trackedPickupUniqueIDs.isEmpty()) {
			int legacyPickupUniqueID = save.getInt("charcoalPickupUniqueID", 0, false);
			boolean hasLegacyPickupUniqueID = save.getBoolean("hasCharcoalPickupUniqueID", legacyPickupUniqueID != 0, false);
			if (hasLegacyPickupUniqueID && legacyPickupUniqueID != 0) trackedPickupUniqueIDs.add(legacyPickupUniqueID);
		}
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		for (Integer pickupUniqueID : trackedPickupUniqueIDs) {
			if (pickupUniqueID == null) continue;
			SaveData pickupSave = new SaveData("PICKUP");
			pickupSave.addInt("pickupUniqueID", pickupUniqueID);
			save.addSaveData(pickupSave);
		}
	}

	@Override
	public boolean shouldSave() {
		return true;
	}

	@Override
	public boolean isValid() {
		return super.isValid()
				&& getLevel().getTileID(tileX, tileY) == TileRegistry.getTileID(ShallowHoleTile.stringID)
				&& getLevel().getObjectID(tileX, tileY) == 0;
	}

	private JobSequence getJobSequence(EntityJobWorker worker, JobTypeHandler.TypePriority priority) {
		resolveTrackedPickups();
		Logging.logMessage(
				"[CharcoalCleanup] getJobSequence: worker=" + worker.getMobWorker().getUniqueID()
						+ ", pit=" + tileX + "," + tileY
						+ ", jobValid=" + isValid()
						+ ", tileID=" + getLevel().getTileID(tileX, tileY)
						+ ", pickup=" + describePickups()
		);

		if (!isValid()) {
			Logging.logMessage("[CharcoalCleanup] Rejecting sequence because cleanup job is invalid at " + tileX + "," + tileY);
			return null;
		}

		LinkedListJobSequence sequence = new LinkedListJobSequence(
				new LocalMessage("activities", "charcoalproduction"),
				false
		);

		List<ActiveJob> inventoryDropOffJobs = new ArrayList<>();
		if (!addCurrentInventoryDropOffJobs(worker, priority, inventoryDropOffJobs)) {
			Logging.logMessage("[FiringInventory] Cleanup job waiting because current work inventory cannot be fully deposited: worker="
					+ worker.getMobWorker().getUniqueID() + ", pit=" + tileX + "," + tileY);
			return null;
		}
		sequence.addAll(inventoryDropOffJobs);

		resolveTrackedPickups();
		boolean hasAnyOutputs = false;
		boolean inventoryWillBeEmptied = !inventoryDropOffJobs.isEmpty();
		for (ItemPickupEntity pickup : new ArrayList<>(trackedPickups)) {
			if (pickup == null || pickup.removed() || pickup.item == null || pickup.item.getAmount() <= 0) continue;
			hasAnyOutputs = true;
			Logging.logMessage("[CharcoalCleanup] Valid output pickup found; building pickup/dropoff actions for " + tileX + "," + tileY + " item=" + pickup.item.item.getStringID());
			if (!addOutputHaulingJobs(worker, priority, sequence, inventoryWillBeEmptied, pickup)) {
				Logging.logMessage("[CharcoalCleanup] Could not build hauling actions for output item at " + tileX + "," + tileY + " item=" + pickup.item.item.getStringID());
				cancelPlannedJobs(inventoryDropOffJobs);
				return null;
			}
			inventoryWillBeEmptied = false;
		}
		if (!hasAnyOutputs) {
			Logging.logMessage("[CharcoalCleanup] No valid tracked output pickups at sequence creation for " + tileX + "," + tileY + ": " + describePickups());
		}

		Logging.logMessage("[CharcoalCleanup] Appending finish/reuse/fill action for " + tileX + "," + tileY);
		sequence.add(new FinishCleanupActiveJob(worker, priority));
		return sequence;
	}

	private boolean addOutputHaulingJobs(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			LinkedListJobSequence sequence,
			boolean inventoryWillBeEmptied,
			ItemPickupEntity outputPickup
	) {
		int pickupAvailable = outputPickup.getAvailableAmount();
		int inventoryCapacity = inventoryWillBeEmptied
				? outputPickup.item.itemStackSize()
				: worker.getWorkInventory().getCanAddAmount(outputPickup.item);
		int availableAmount = Math.min(pickupAvailable, inventoryCapacity);
		Logging.logMessage(
				"[CharcoalCleanup] Haul check: worker=" + worker.getMobWorker().getUniqueID()
						+ ", pit=" + tileX + "," + tileY
						+ ", pickupAvailable=" + pickupAvailable
						+ ", inventoryCapacity=" + inventoryCapacity
						+ ", inventoryWillBeEmptied=" + inventoryWillBeEmptied
						+ ", haulAmount=" + availableAmount
		);
		if (availableAmount <= 0) {
			Logging.logMessage("[CharcoalCleanup] Haul rejected because available amount/carry capacity is zero at " + tileX + "," + tileY);
			return false;
		}

		InventoryItem requestedItem = outputPickup.item.copy(
				Math.min(availableAmount, outputPickup.item.itemStackSize())
		);
		ArrayList dropOffLocations = HasStorageLevelJob.findDropOffLocation(
				worker,
				requestedItem,
				outputPickup.getPositionPoint()
		);
		Logging.logMessage(
				"[CharcoalCleanup] Storage search: requested=" + requestedItem.getAmount()
						+ " " + requestedItem.item.getStringID() + ", locations=" + dropOffLocations.size()
						+ ", pickupPos=" + outputPickup.getPositionPoint()
		);

		int dropOffAmount = 0;
		LinkedList<DropOffSettlementStorageActiveJob> dropOffJobs = new LinkedList<>();
		for (Object value : dropOffLocations) {
			HasStorageLevelJob.DropOffFind dropOff = (HasStorageLevelJob.DropOffFind)value;
			dropOffAmount += dropOff.item.getAmount();
			dropOffJobs.add(dropOff.getActiveJob(
					worker,
					priority,
					CharcoalCleanupLevelJob.this.reservable,
					false
			));
		}

		Logging.logMessage("[CharcoalCleanup] Storage search total accepted amount=" + dropOffAmount + ", jobs=" + dropOffJobs.size());
		if (dropOffAmount <= 0) {
			Logging.logMessage("[CharcoalCleanup] Haul rejected because storage search accepted zero output items at " + tileX + "," + tileY);
			return false;
		}

		ItemPickupReservedAmount pickupReservation = outputPickup.reservePickupAmount(dropOffAmount);
		if (pickupReservation == null) {
			Logging.logMessage("[CharcoalCleanup] Failed to reserve " + dropOffAmount + " output items from tracked pickup at " + tileX + "," + tileY);
			return false;
		}
		Logging.logMessage("[CharcoalCleanup] Reserved " + dropOffAmount + " output pickup amount at " + tileX + "," + tileY);

		sequence.add(new PickupItemEntityActiveJob(worker, priority, pickupReservation) {
			private Boolean lastCurrent;
			private Boolean lastMoving;

			@Override
			public void tick(boolean isCurrent, boolean isMovingTo) {
				CharcoalCleanupLevelJob.this.reservable.reserve(worker.getMobWorker());
				if (lastCurrent == null || lastCurrent != isCurrent || lastMoving == null || lastMoving != isMovingTo) {
					Logging.logMessage(
							"[CharcoalCleanup] Pickup action state: worker=" + worker.getMobWorker().getUniqueID()
									+ ", current=" + isCurrent
									+ ", moving=" + isMovingTo
									+ ", pickup=" + describePickups()
					);
					lastCurrent = isCurrent;
					lastMoving = isMovingTo;
				}
				super.tick(isCurrent, isMovingTo);
			}

			@Override
			public ActiveJobResult perform() {
				Logging.logMessage("[CharcoalCleanup] Pickup perform before: " + describePickups());
				ActiveJobResult result = super.perform();
				Logging.logMessage("[CharcoalCleanup] Pickup perform after: result=" + result + ", pickups=" + describePickups());
				return result;
			}

			@Override
			public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
				Logging.logMessage(
						"[CharcoalCleanup] Pickup action cancelled: invalid=" + becauseOfInvalid
								+ ", current=" + isCurrent
								+ ", moving=" + isMovingTo
								+ ", pickup=" + describePickups()
				);
				super.onCancelled(becauseOfInvalid, isCurrent, isMovingTo);
			}
		});

		for (DropOffSettlementStorageActiveJob dropOffJob : dropOffJobs) {
			sequence.add(dropOffJob);
		}

		return true;
	}

	private boolean addCurrentInventoryDropOffJobs(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			List<ActiveJob> jobs
	) {
		List<InventoryItem> currentItems = ClayPackageSystem.getExpandedContents(worker.getWorkInventory().items());

		if (!currentItems.isEmpty()) {
			Logging.logMessage("[FiringInventory] Planning deposit of " + currentItems.size()
					+ " work-inventory stack(s) before charcoal cleanup for worker="
					+ worker.getMobWorker().getUniqueID() + ", pit=" + tileX + "," + tileY);
		}

		for (InventoryItem item : currentItems) {
			ArrayList<HasStorageLevelJob.DropOffFind> dropOffLocations =
					HasStorageLevelJob.findDropOffLocation(worker, item);
			int dropOffCapacity = 0;

			for (HasStorageLevelJob.DropOffFind location : dropOffLocations) {
				dropOffCapacity += location.item.getAmount();
			}

			if (dropOffCapacity < item.getAmount()) {
				Logging.logMessage("[FiringInventory] Cannot fully deposit " + item.item.getStringID()
						+ " x" + item.getAmount() + " before charcoal cleanup; capacity=" + dropOffCapacity
						+ ", pit=" + tileX + "," + tileY);
				cancelPlannedJobs(jobs);
				return false;
			}

			for (HasStorageLevelJob.DropOffFind location : dropOffLocations) {
				jobs.add(location.getActiveJob(worker, priority, null, false));
			}
		}

		return true;
	}

	private static void cancelPlannedJobs(List<ActiveJob> jobs) {
		for (ActiveJob job : jobs) {
			job.onCancelled(true, false, false);
		}
		jobs.clear();
	}

	private static ArrayList<Integer> getPickupUniqueIDs(List<ItemPickupEntity> pickups) {
		ArrayList<Integer> result = new ArrayList<>();
		if (pickups != null) {
			for (ItemPickupEntity pickup : pickups) {
				if (pickup != null) result.add(pickup.getUniqueID());
			}
		}
		return result;
	}

	private void setTrackedPickups(List<ItemPickupEntity> pickups) {
		trackedPickups.clear();
		if (pickups != null) {
			for (ItemPickupEntity pickup : pickups) {
				if (pickup != null && !trackedPickups.contains(pickup)) trackedPickups.add(pickup);
			}
		}
	}

	private void resolveTrackedPickups() {
		trackedPickups.clear();
		LinkedHashSet<Integer> uniqueIDs = new LinkedHashSet<>(trackedPickupUniqueIDs);
		for (Integer pickupUniqueID : uniqueIDs) {
			if (pickupUniqueID == null || pickupUniqueID == 0 || getLevel() == null) continue;
			Object pickup = getLevel().entityManager.pickups.get(pickupUniqueID, false);
			if (pickup instanceof ItemPickupEntity) {
				ItemPickupEntity itemPickup = (ItemPickupEntity)pickup;
				if (itemPickup.item != null && itemPickup.item.getAmount() > 0 && !itemPickup.removed()) {
					trackedPickups.add(itemPickup);
				}
			}
		}
	}

	private String describePickups() {
		resolveTrackedPickups();
		if (trackedPickups.isEmpty()) return "none";
		ArrayList<String> descriptions = new ArrayList<>();
		for (ItemPickupEntity pickup : trackedPickups) {
			String itemID = pickup.item == null ? "null" : pickup.item.item.getStringID();
			int amount = pickup.item == null ? 0 : pickup.item.getAmount();
			descriptions.add(itemID + " amount=" + amount + " available=" + pickup.getAvailableAmount() + " removed=" + pickup.removed());
		}
		return String.join(", ", descriptions);
	}

	private boolean hasTrackedPickup(String stringID) {
		resolveTrackedPickups();
		for (ItemPickupEntity pickup : trackedPickups) {
			if (pickup != null && pickup.item != null && stringID.equals(pickup.item.item.getStringID()) && pickup.item.getAmount() > 0 && !pickup.removed()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasAnyTrackedPickup() {
		resolveTrackedPickups();
		return !trackedPickups.isEmpty();
	}

	private CharcoalProductionZone findProductionZone(EntityJobWorker worker) {
		if (!(worker.getMobWorker() instanceof SettlerMob)) {
			return null;
		}

		SettlerMob settler = (SettlerMob)worker.getMobWorker();
		ServerSettlementData settlement = settler.getSettlerSettlementServerData();
		if (settlement == null) {
			return null;
		}

		for (SettlementWorkZone zone : settlement.getWorkZones().getZones().values()) {
			if (zone instanceof CharcoalProductionZone
					&& !zone.isRemoved()
					&& zone.containsTile(tileX, tileY)) {
				return (CharcoalProductionZone)zone;
			}
		}

		return null;
	}

	private class FinishCleanupActiveJob extends TileActiveJob {
		private boolean fillStarted;
		private long fillCompleteTime;

		FinishCleanupActiveJob(EntityJobWorker worker, JobTypeHandler.TypePriority priority) {
			super(worker, priority, CharcoalCleanupLevelJob.this.tileX, CharcoalCleanupLevelJob.this.tileY);
		}

		@Override
		public JobMoveToTile getMoveToTile(JobMoveToTile lastTile) {
			return new JobMoveToTile(tileX, tileY, true);
		}

		@Override
		public int getCompleteRange() {
			return 8;
		}

		@Override
		public void tick(boolean isCurrent, boolean isMovingTo) {
			CharcoalCleanupLevelJob.this.reservable.reserve(worker.getMobWorker());
			if (isCurrent && !isMovingTo && fillStarted) {
				worker.showWorkAnimation(
						tileX * 32 + 16,
						tileY * 32 + 16,
						ItemRegistry.getItem("ironshovel"),
						1000,
						true
				);
			}
		}

		@Override
		public boolean isValid(boolean isCurrent) {
			boolean jobValid = CharcoalCleanupLevelJob.this.isValid();
			boolean reservationValid = CharcoalCleanupLevelJob.this.reservable.isAvailable(worker.getMobWorker());
			boolean valid = jobValid && reservationValid;
			if (!valid) {
				Logging.logMessage(
						"[CharcoalCleanup] Finish action invalid: worker=" + worker.getMobWorker().getUniqueID()
								+ ", current=" + isCurrent
								+ ", jobValid=" + jobValid
								+ ", reservationAvailable=" + reservationValid
								+ ", tileID=" + getLevel().getTileID(tileX, tileY)
								+ ", pickup=" + describePickups()
				);
			}
			return valid;
		}

		@Override
		public ActiveJobResult perform() {
			Logging.logMessage(
					"[CharcoalCleanup] Finish perform: worker=" + worker.getMobWorker().getUniqueID()
							+ ", pit=" + tileX + "," + tileY
							+ ", fillStarted=" + fillStarted
							+ ", pickup=" + describePickups()
			);
			if (hasAnyTrackedPickup()) {
				Logging.logMessage("[CharcoalCleanup] Finish action failed because tracked output pickups still exist at " + tileX + "," + tileY);
				return ActiveJobResult.FAILED;
			}

			CharcoalProductionZone zone = findProductionZone(worker);
			Logging.logMessage(
					"[CharcoalCleanup] Production decision: zone=" + (zone != null)
							+ ", canProduce=" + (zone != null && zone.canProduce())
							+ ", reusable=" + (zone != null && CharcoalProductionZone.isValidReusableHole(getLevel(), tileX, tileY, null))
			);
			if (zone != null
					&& zone.canProduce()
					&& CharcoalProductionZone.isValidReusableHole(
							getLevel(),
							tileX,
							tileY,
							null
					)) {
				Logging.logMessage("[CharcoalCleanup] Reusing shallow hole for another charcoal batch at " + tileX + "," + tileY);
				opusliews.tile.CharcoalPitLevelData pitData = opusliews.tile.CharcoalPitLevelData.get(getLevel(), true);
				pitData.clearPendingCleanup(tileX, tileY);
				pitData.setProductionRecoveryState(
						tileX, tileY, opusliews.tile.CharcoalPitLevelData.ProductionStage.DUG, zone.getUniqueID());
				CharcoalCleanupLevelJob.this.remove();
				getLevel().jobsLayer.addJob(new CharcoalProductionLevelJob(tileX, tileY, zone, true));
				return ActiveJobResult.FINISHED;
			}

			long currentTime = getLevel().getTime();
			if (!fillStarted) {
				fillStarted = true;
				fillCompleteTime = currentTime + holeFillTime;
				Logging.logMessage("[CharcoalCleanup] Starting 2-second hole fill at " + tileX + "," + tileY + ", completesAt=" + fillCompleteTime);
				return ActiveJobResult.PERFORMING;
			}

			if (currentTime < fillCompleteTime) {
				return ActiveJobResult.PERFORMING;
			}

			if (getLevel().getTileID(tileX, tileY) != TileRegistry.getTileID(ShallowHoleTile.stringID)
					|| getLevel().getObjectID(tileX, tileY) != 0) {
				Logging.logMessage("[CharcoalCleanup] Hole fill failed because the shallow hole is no longer empty at "
						+ tileX + "," + tileY + ", tileID=" + getLevel().getTileID(tileX, tileY)
						+ ", objectID=" + getLevel().getObjectID(tileX, tileY));
				return ActiveJobResult.FAILED;
			}

			ShallowHoleSystem.fillHoleWithThinDirt(getLevel(), tileX, tileY);
			getLevel().sendTileUpdatePacket(tileX, tileY);
			getLevel().getLevelTile(tileX, tileY).checkAround();
			getLevel().getLevelObject(tileX, tileY).checkAround();
			getLevel().getServer().network.sendToClientsWithTile(
					new PacketBuilderTilePlaceSound(TileRegistry.dirtID, tileX, tileY),
					getLevel(),
					tileX,
					tileY
			);

			opusliews.tile.CharcoalPitLevelData.get(getLevel(), true).clearPendingCleanup(tileX, tileY);
			Logging.logMessage("[CharcoalCleanup] Filled shallow hole with dirt and completed cleanup at " + tileX + "," + tileY);
			CharcoalCleanupLevelJob.this.remove();
			return ActiveJobResult.FINISHED;
		}

		@Override
		public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
			Logging.logMessage(
					"[CharcoalCleanup] Finish action cancelled: invalid=" + becauseOfInvalid
							+ ", current=" + isCurrent
							+ ", moving=" + isMovingTo
							+ ", pit=" + tileX + "," + tileY
							+ ", pickup=" + describePickups()
			);
			super.onCancelled(becauseOfInvalid, isCurrent, isMovingTo);
		}
	}

	public static JobSequence getJobSequence(EntityJobWorker worker, FoundJob foundJob) {
		return ((CharcoalCleanupLevelJob)foundJob.job).getJobSequence(worker, foundJob.priority);
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		if (!(worker.getMobWorker() instanceof HumanMob)) {
			return null;
		}

		HumanMob human = (HumanMob)worker.getMobWorker();
		return handler
				.setJobHandler(CharcoalCleanupLevelJob.class, foundJob -> getJobSequence(foundJob.worker, foundJob))
				.setPredicate(
						() -> !human.isOnStrike()
								&& !human.hasCompletedMission()
								&& (!human.isSettler() || human.isSettlerWithinSettlement())
				);
	}
}
