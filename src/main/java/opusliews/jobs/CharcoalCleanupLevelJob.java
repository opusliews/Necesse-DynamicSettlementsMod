package opusliews.jobs;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.save.LoadData;
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
import opusliews.logging.Logging;
import opusliews.network.PacketBuilderTilePlaceSound;
import opusliews.tile.ShallowHoleTile;

public class CharcoalCleanupLevelJob extends TileLevelJob {
	private static final long holeFillTime = 2000L;
	private ItemPickupEntity charcoalPickup;

	public CharcoalCleanupLevelJob(int tileX, int tileY, ItemPickupEntity charcoalPickup) {
		super(tileX, tileY);
		this.charcoalPickup = charcoalPickup;
	}

	public CharcoalCleanupLevelJob(LoadData save) {
		super(save);
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isValid() {
		return super.isValid()
				&& getLevel().getTileID(tileX, tileY) == TileRegistry.getTileID(ShallowHoleTile.stringID);
	}

	private JobSequence getJobSequence(EntityJobWorker worker, JobTypeHandler.TypePriority priority) {
		Logging.logMessage(
				"[CharcoalCleanup] getJobSequence: worker=" + worker.getMobWorker().getUniqueID()
						+ ", pit=" + tileX + "," + tileY
						+ ", jobValid=" + isValid()
						+ ", tileID=" + getLevel().getTileID(tileX, tileY)
						+ ", pickup=" + describePickup()
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

		if (hasCharcoalPickup()) {
			Logging.logMessage("[CharcoalCleanup] Valid charcoal pickup found; building pickup/dropoff actions for " + tileX + "," + tileY);
			if (!addCharcoalHaulingJobs(worker, priority, sequence, !inventoryDropOffJobs.isEmpty())) {
				Logging.logMessage("[CharcoalCleanup] Could not build charcoal hauling actions for " + tileX + "," + tileY);
				cancelPlannedJobs(inventoryDropOffJobs);
				return null;
			}
		}
		else {
			Logging.logMessage("[CharcoalCleanup] No valid tracked charcoal pickup at sequence creation for " + tileX + "," + tileY + ": " + describePickup());
		}

		Logging.logMessage("[CharcoalCleanup] Appending finish/reuse/fill action for " + tileX + "," + tileY);
		sequence.add(new FinishCleanupActiveJob(worker, priority));
		return sequence;
	}

	private boolean addCharcoalHaulingJobs(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			LinkedListJobSequence sequence,
			boolean inventoryWillBeEmptied
	) {
		int pickupAvailable = charcoalPickup.getAvailableAmount();
		int inventoryCapacity = inventoryWillBeEmptied
				? charcoalPickup.item.itemStackSize()
				: worker.getWorkInventory().getCanAddAmount(charcoalPickup.item);
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

		InventoryItem requestedItem = charcoalPickup.item.copy(
				Math.min(availableAmount, charcoalPickup.item.itemStackSize())
		);
		ArrayList dropOffLocations = HasStorageLevelJob.findDropOffLocation(
				worker,
				requestedItem,
				charcoalPickup.getPositionPoint()
		);
		Logging.logMessage(
				"[CharcoalCleanup] Storage search: requested=" + requestedItem.getAmount()
						+ " charcoal, locations=" + dropOffLocations.size()
						+ ", pickupPos=" + charcoalPickup.getPositionPoint()
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
			Logging.logMessage("[CharcoalCleanup] Haul rejected because storage search accepted zero charcoal at " + tileX + "," + tileY);
			return false;
		}

		ItemPickupReservedAmount pickupReservation = charcoalPickup.reservePickupAmount(dropOffAmount);
		if (pickupReservation == null) {
			Logging.logMessage("[CharcoalCleanup] Failed to reserve " + dropOffAmount + " charcoal from tracked pickup at " + tileX + "," + tileY);
			return false;
		}
		Logging.logMessage("[CharcoalCleanup] Reserved " + dropOffAmount + " charcoal pickup amount at " + tileX + "," + tileY);

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
									+ ", pickup=" + describePickup()
					);
					lastCurrent = isCurrent;
					lastMoving = isMovingTo;
				}
				super.tick(isCurrent, isMovingTo);
			}

			@Override
			public ActiveJobResult perform() {
				Logging.logMessage("[CharcoalCleanup] Pickup perform before: " + describePickup());
				ActiveJobResult result = super.perform();
				Logging.logMessage("[CharcoalCleanup] Pickup perform after: result=" + result + ", pickup=" + describePickup());
				return result;
			}

			@Override
			public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
				Logging.logMessage(
						"[CharcoalCleanup] Pickup action cancelled: invalid=" + becauseOfInvalid
								+ ", current=" + isCurrent
								+ ", moving=" + isMovingTo
								+ ", pickup=" + describePickup()
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
		List<InventoryItem> currentItems = new ArrayList<>();

		for (InventoryItem item : worker.getWorkInventory().items()) {
			if (item != null && item.getAmount() > 0) {
				currentItems.add(item.copy());
			}
		}

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

	private String describePickup() {
		if (charcoalPickup == null) return "null";
		String itemID = charcoalPickup.item == null ? "null" : charcoalPickup.item.item.getStringID();
		int amount = charcoalPickup.item == null ? 0 : charcoalPickup.item.getAmount();
		return "removed=" + charcoalPickup.removed()
				+ ", item=" + itemID
				+ ", amount=" + amount
				+ ", available=" + charcoalPickup.getAvailableAmount()
				+ ", pos=" + charcoalPickup.getPositionPoint();
	}

	private boolean hasCharcoalPickup() {
		return charcoalPickup != null
				&& !charcoalPickup.removed()
				&& charcoalPickup.item != null
				&& "charcoal".equals(charcoalPickup.item.item.getStringID())
				&& charcoalPickup.item.getAmount() > 0;
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
								+ ", pickup=" + describePickup()
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
							+ ", pickup=" + describePickup()
			);
			if (hasCharcoalPickup()) {
				Logging.logMessage("[CharcoalCleanup] Finish action failed because tracked charcoal pickup still exists at " + tileX + "," + tileY);
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

			if (getLevel().getTileID(tileX, tileY) != TileRegistry.getTileID(ShallowHoleTile.stringID)) {
				Logging.logMessage("[CharcoalCleanup] Hole fill failed because tile is no longer shallow hole at " + tileX + "," + tileY + ", tileID=" + getLevel().getTileID(tileX, tileY));
				return ActiveJobResult.FAILED;
			}

			getLevel().setTile(tileX, tileY, TileRegistry.dirtID);
			getLevel().sendTileUpdatePacket(tileX, tileY);
			getLevel().getLevelTile(tileX, tileY).checkAround();
			getLevel().getLevelObject(tileX, tileY).checkAround();
			getLevel().getServer().network.sendToClientsWithTile(
					new PacketBuilderTilePlaceSound(TileRegistry.dirtID, tileX, tileY),
					getLevel(),
					tileX,
					tileY
			);

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
							+ ", pickup=" + describePickup()
			);
		}
	}

	public static JobSequence getJobSequence(EntityJobWorker worker, FoundJob foundJob) {
		return ((CharcoalCleanupLevelJob)foundJob.job).getJobSequence(worker, foundJob.priority);
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		if (!(worker instanceof HumanMob)) {
			return null;
		}

		HumanMob human = (HumanMob)worker;
		return handler
				.setJobHandler(CharcoalCleanupLevelJob.class, foundJob -> getJobSequence(human, foundJob))
				.setPredicate(
						() -> !human.isOnStrike()
								&& !human.hasCompletedMission()
								&& (!human.isSettler() || human.isSettlerWithinSettlement())
				);
	}
}
