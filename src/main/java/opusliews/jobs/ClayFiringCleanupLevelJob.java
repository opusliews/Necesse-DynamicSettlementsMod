package opusliews.jobs;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import necesse.engine.localization.message.LocalMessage;
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
import necesse.entity.mobs.job.activeJob.PickupItemEntityActiveJob;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.entity.pickup.ItemPickupEntity;
import necesse.entity.pickup.ItemPickupReservedAmount;
import necesse.inventory.InventoryItem;
import necesse.level.maps.levelData.jobs.HasStorageLevelJob;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import opusliews.charcoal.CharcoalProductionZone;
import opusliews.clay.ClayPackageSystem;
import opusliews.clayfiring.ClayFiringAutomationLevelData;
import opusliews.clayfiring.ClayFiringAutomationLevelData.PendingBatch;
import opusliews.clayfiring.ClayFiringZone;
import opusliews.logging.Logging;
import opusliews.tile.FireHazardPathing;
import opusliews.tile.ShallowHoleSystem;
import opusliews.tile.ShallowHoleTile;

public class ClayFiringCleanupLevelJob extends TileLevelJob {
	private final int zoneUniqueID;

	public ClayFiringCleanupLevelJob(int tileX, int tileY, int zoneUniqueID) {
		super(tileX, tileY);
		this.zoneUniqueID = zoneUniqueID;
	}

	public ClayFiringCleanupLevelJob(LoadData save) {
		super(save);
		this.zoneUniqueID = 0;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isValid() {
		if (!super.isValid()) return false;
		ClayFiringAutomationLevelData data = ClayFiringAutomationLevelData.get(getLevel(), false);
		return data != null && data.getPendingBatch(tileX, tileY) != null;
	}

	private TileActiveJob getFinishActiveJob(EntityJobWorker worker, JobTypeHandler.TypePriority priority) {
		return new TileActiveJob(worker, priority, tileX, tileY) {
			private Point workTile;
			private long completeTime;
			private boolean started;

			@Override
			public JobMoveToTile getMoveToTile(JobMoveToTile lastTile) {
				HumanMob mob = (HumanMob)worker.getMobWorker();
				if (workTile != null && mob.estimateCanMoveTo(workTile.x, workTile.y, false)) {
					return new JobMoveToTile(workTile.x, workTile.y, false);
				}
				for (int[] offset : CharcoalProductionZone.cardinalOffsets) {
					Point candidate = new Point(tileX + offset[0], tileY + offset[1]);
					if (!getLevel().isTileWithinBounds(candidate.x, candidate.y)) continue;
					if (FireHazardPathing.isFireHazard(getLevel(), candidate.x, candidate.y)) continue;
					if (!mob.estimateCanMoveTo(candidate.x, candidate.y, false)) continue;
					workTile = candidate;
					return new JobMoveToTile(candidate.x, candidate.y, false);
				}
				return null;
			}

			@Override
			public int getCompleteRange() {
				return 8;
			}

			@Override
			public void tick(boolean isCurrent, boolean isMovingTo) {
				ClayFiringCleanupLevelJob.this.reservable.reserve(worker.getMobWorker());
			}

			@Override
			public boolean isValid(boolean isCurrent) {
				return ClayFiringCleanupLevelJob.this.isValid()
						&& !ClayFiringCleanupLevelJob.this.isRemoved()
						&& ClayFiringCleanupLevelJob.this.reservable.isAvailable(worker.getMobWorker());
			}

			@Override
			public ActiveJobResult perform() {
				if (!started) {
					started = true;
					completeTime = getLevel().getTime() + 1000L;
					return ActiveJobResult.PERFORMING;
				}
				if (getLevel().getTime() < completeTime) return ActiveJobResult.PERFORMING;

				ClayFiringAutomationLevelData data = ClayFiringAutomationLevelData.get(getLevel(), false);
				PendingBatch pending = data == null ? null : data.getPendingBatch(tileX, tileY);
				if (pending == null) return ActiveJobResult.FAILED;
				if (!data.getPendingPickups(tileX, tileY).isEmpty()) return ActiveJobResult.FAILED;

				data.removePendingBatch(tileX, tileY);
				ClayFiringZone zone = data.findZone(tileX, tileY, pending.zoneUniqueID == 0 ? zoneUniqueID : pending.zoneUniqueID);
				boolean reused = zone != null
						&& zone.canProduce()
						&& zone.hasEnoughMaterials()
						&& ClayFiringZone.isValidReusableHole(getLevel(), tileX, tileY, null);
				if (reused) {
					getLevel().jobsLayer.addJob(new ClayFiringProductionLevelJob(tileX, tileY, zone, true));
				} else if (getLevel().getTileID(tileX, tileY) == TileRegistry.getTileID(ShallowHoleTile.stringID)
						&& getLevel().getObjectID(tileX, tileY) == 0) {
					getLevel().setTile(tileX, tileY, ShallowHoleSystem.getFillTileID(getLevel(), tileX, tileY));
					getLevel().sendTileUpdatePacket(tileX, tileY);
					getLevel().getLevelTile(tileX, tileY).checkAround();
					getLevel().getLevelObject(tileX, tileY).checkAround();
				}
				Logging.logMessage("[ClayFiring] Completed world-pickup cleanup at " + tileX + "," + tileY + "; reuse=" + reused);
				ClayFiringCleanupLevelJob.this.remove();
				return ActiveJobResult.FINISHED;
			}
		};
	}

	public static JobSequence getJobSequence(EntityJobWorker worker, FoundJob foundJob) {
		ClayFiringCleanupLevelJob job = (ClayFiringCleanupLevelJob)foundJob.job;
		if (!job.isValid()) return null;

		ClayFiringAutomationLevelData data = ClayFiringAutomationLevelData.get(job.getLevel(), false);
		PendingBatch pending = data == null ? null : data.getPendingBatch(job.tileX, job.tileY);
		if (pending == null) return null;

		LinkedListJobSequence sequence = new LinkedListJobSequence(new LocalMessage("activities", "clayfiringrecovery"), false);
		ArrayList<ActiveJob> plannedJobs = new ArrayList<>();
		if (!addCurrentInventoryDropOffJobs(worker, foundJob.priority, plannedJobs)) return null;
		sequence.addAll(plannedJobs);

		List<ItemPickupEntity> pickups = data.getPendingPickups(job.tileX, job.tileY);
		for (ItemPickupEntity pickup : pickups) {
			if (pickup == null || pickup.removed() || pickup.item == null || pickup.item.getAmount() <= 0) continue;
			int amount = Math.min(pickup.getAvailableAmount(), pickup.item.getAmount());
			if (amount <= 0) continue;

			InventoryItem requested = pickup.item.copy(amount);
			ArrayList<HasStorageLevelJob.DropOffFind> dropOffLocations = HasStorageLevelJob.findDropOffLocation(worker, requested, pickup.getPositionPoint());
			int capacity = 0;
			for (HasStorageLevelJob.DropOffFind location : dropOffLocations) capacity += location.item.getAmount();
			if (capacity < amount) {
				cancelPlannedJobs(plannedJobs);
				return null;
			}

			ItemPickupReservedAmount reservation = pickup.reservePickupAmount(amount);
			if (reservation == null) {
				cancelPlannedJobs(plannedJobs);
				return null;
			}
			pickup.pickupCooldown = 5000;
			ClayPickupActiveJob pickupJob = new ClayPickupActiveJob(worker, foundJob.priority, reservation, job);
			sequence.add(pickupJob);
			plannedJobs.add(pickupJob);

			for (HasStorageLevelJob.DropOffFind location : dropOffLocations) {
				ActiveJob dropOff = location.getActiveJob(worker, foundJob.priority, job.reservable, false);
				sequence.add(dropOff);
				plannedJobs.add(dropOff);
			}
		}

		sequence.add(job.getFinishActiveJob(worker, foundJob.priority));
		return sequence;
	}

	private static boolean addCurrentInventoryDropOffJobs(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			List<ActiveJob> jobs
	) {
		ArrayList<InventoryItem> toDeposit = new ArrayList<>();
		for (Object value : worker.getWorkInventory().items()) {
			if (!(value instanceof InventoryItem)) continue;
			InventoryItem item = (InventoryItem)value;
			if (item == null || item.getAmount() <= 0) continue;
			if (ClayPackageSystem.isPackage(item)) toDeposit.addAll(ClayPackageSystem.getContents(item));
			else toDeposit.add(item.copy());
		}

		for (InventoryItem item : toDeposit) {
			ArrayList<HasStorageLevelJob.DropOffFind> locations = HasStorageLevelJob.findDropOffLocation(worker, item.copy());
			int capacity = 0;
			for (HasStorageLevelJob.DropOffFind location : locations) capacity += location.item.getAmount();
			if (capacity < item.getAmount()) {
				cancelPlannedJobs(jobs);
				return false;
			}
			for (HasStorageLevelJob.DropOffFind location : locations) jobs.add(location.getActiveJob(worker, priority, null, false));
		}
		return true;
	}

	private static void cancelPlannedJobs(List<ActiveJob> jobs) {
		for (ActiveJob job : jobs) job.onCancelled(true, false, false);
		jobs.clear();
	}

	private static class ClayPickupActiveJob extends PickupItemEntityActiveJob {
		private final ClayFiringCleanupLevelJob cleanupJob;

		ClayPickupActiveJob(
				EntityJobWorker worker,
				JobTypeHandler.TypePriority priority,
				ItemPickupReservedAmount pickup,
				ClayFiringCleanupLevelJob cleanupJob
		) {
			super(worker, priority, pickup);
			this.cleanupJob = cleanupJob;
		}

		@Override
		public void tick(boolean isCurrent, boolean isMovingTo) {
			cleanupJob.reservable.reserve(worker.getMobWorker());
			super.tick(isCurrent, isMovingTo);
		}

		@Override
		public ActiveJobResult performTarget() {
			if (worker.isInWorkAnimation()) return ActiveJobResult.PERFORMING;
			boolean first = true;
			for (Iterator iterator = pickups.iterator(); iterator.hasNext();) {
				ItemPickupReservedAmount pickup = (ItemPickupReservedAmount)iterator.next();
				InventoryItem item = pickup.pickupItem();
				if (item == null || item.getAmount() <= 0) continue;
				if (!ClayPackageSystem.addToWorkInventory(worker.getWorkInventory(), item)) {
					ItemPickupEntity restored = item.getPickupEntity(getLevel(), ((ItemPickupEntity)target).getX(), ((ItemPickupEntity)target).getY());
					getLevel().entityManager.pickups.add(restored);
					return ActiveJobResult.FAILED;
				}
				if (first) {
					worker.showPickupAnimation(((ItemPickupEntity)target).getX(), ((ItemPickupEntity)target).getY(), item.item, 250, true);
					first = false;
				}
				Logging.logMessage("[ClayFiring] Picked fired world item into clay package: "
						+ item.item.getStringID() + " x" + item.getAmount());
			}
			return ActiveJobResult.FINISHED;
		}
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		if (!(worker instanceof HumanMob)) return null;
		HumanMob human = (HumanMob)worker;
		return handler.setJobHandler(ClayFiringCleanupLevelJob.class, foundJob -> getJobSequence(human, foundJob))
				.setPredicate(() -> !human.isOnStrike() && !human.hasCompletedMission()
						&& (!human.isSettler() || human.isSettlerWithinSettlement()));
	}
}
