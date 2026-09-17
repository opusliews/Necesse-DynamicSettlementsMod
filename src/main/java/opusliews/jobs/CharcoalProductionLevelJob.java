package opusliews.jobs;

import java.util.LinkedList;
import java.util.List;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.GlobalIngredientRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.entity.mobs.job.LinkedListJobSequence;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import necesse.level.maps.levelData.settlementData.SettlementStoragePickupSlot;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageGlobalIngredientIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;
import opusliews.charcoal.CharcoalProductionZone;
import opusliews.network.PacketBuilderTilePlaceSound;

public class CharcoalProductionLevelJob extends TileLevelJob {
	public static final int requiredLogs = 32;
	private static final long grassRemovalTime = 2000L;

	private final CharcoalProductionZone zone;

	public CharcoalProductionLevelJob(int tileX, int tileY, CharcoalProductionZone zone) {
		super(tileX, tileY);
		this.zone = zone;
	}

	public CharcoalProductionLevelJob(LoadData save) {
		super(save);
		this.zone = null;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isValid() {
		return super.isValid()
				&& zone != null
				&& !zone.isRemoved()
				&& zone.containsTile(tileX, tileY)
				&& zone.canProduce()
				&& CharcoalProductionZone.isValidCandidate(getLevel(), tileX, tileY, this);
	}

	private TileActiveJob getActiveJob(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			List<SettlementStoragePickupSlot> logReservations
	) {
		return new TileActiveJob(worker, priority, tileX, tileY) {
			private boolean grassRemovalStarted;
			private long grassRemovalCompleteTime;

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
				CharcoalProductionLevelJob.this.reservable.reserve(worker.getMobWorker());
				for (SettlementStoragePickupSlot slot : logReservations) {
					if (!slot.isRemoved()) {
						slot.reserve(worker.getMobWorker());
					}
				}

				if (isCurrent && !isMovingTo && hasGrassTile()) {
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
				return !CharcoalProductionLevelJob.this.isRemoved()
						&& CharcoalProductionLevelJob.this.isValid()
						&& CharcoalProductionLevelJob.this.reservable.isAvailable(worker.getMobWorker())
						&& reservationsAreValid(logReservations);
			}

			@Override
			public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
				super.onCancelled(becauseOfInvalid, isCurrent, isMovingTo);
				releaseReservations(logReservations);
			}

			@Override
			public ActiveJobResult perform() {
				if (!hasGrassTile()) {
					return finishCurrentStep();
				}

				long currentTime = getLevel().getTime();
				if (!grassRemovalStarted) {
					grassRemovalStarted = true;
					grassRemovalCompleteTime = currentTime + grassRemovalTime;
					return ActiveJobResult.PERFORMING;
				}

				if (currentTime < grassRemovalCompleteTime) {
					return ActiveJobResult.PERFORMING;
				}

				if (!hasGrassTile()) {
					return finishCurrentStep();
				}

				getLevel().setObject(tileX, tileY, 0);
				getLevel().setTile(tileX, tileY, TileRegistry.dirtID);
				getLevel().sendObjectUpdatePacket(tileX, tileY);
				getLevel().sendTileUpdatePacket(tileX, tileY);
				getLevel().getServer().network.sendToClientsWithTile(
						new PacketBuilderTilePlaceSound(TileRegistry.dirtID, tileX, tileY),
						getLevel(),
						tileX,
						tileY
				);

				return finishCurrentStep();
			}

			private boolean hasGrassTile() {
				return getLevel().getTileID(tileX, tileY) == TileRegistry.grassID;
			}

			private ActiveJobResult finishCurrentStep() {
				releaseReservations(logReservations);
				CharcoalProductionLevelJob.this.remove();
				return ActiveJobResult.FINISHED;
			}
		};
	}

	public static JobSequence getJobSequence(EntityJobWorker worker, FoundJob foundJob) {
		CharcoalProductionLevelJob job = (CharcoalProductionLevelJob)foundJob.job;
		if (!job.isValid()) {
			return null;
		}

		List<SettlementStoragePickupSlot> logReservations = reserveLogs(worker);
		if (logReservations == null) {
			return null;
		}

		if (!job.isValid()) {
			releaseReservations(logReservations);
			return null;
		}

		LinkedListJobSequence sequence = new LinkedListJobSequence(
				new LocalMessage("activities", "charcoalproduction"),
				false
		);
		sequence.add(job.getActiveJob(worker, foundJob.priority, logReservations));
		return sequence;
	}

	private static List<SettlementStoragePickupSlot> reserveLogs(EntityJobWorker worker) {
		SettlementStorageRecords records = PickupSettlementStorageActiveJob.getStorageRecords(worker);
		if (records == null) {
			return null;
		}

		int anyLogID = GlobalIngredientRegistry.getGlobalIngredientID("anylog");
		SettlementStorageGlobalIngredientIDIndex index = records.getIndex(SettlementStorageGlobalIngredientIDIndex.class);
		LinkedList<SettlementStoragePickupSlot> slots = index.findPickupSlots(
				anyLogID,
				worker,
				null,
				requiredLogs,
				requiredLogs
		);
		return slots;
	}

	private static boolean reservationsAreValid(List<SettlementStoragePickupSlot> reservations) {
		int amount = 0;
		for (SettlementStoragePickupSlot slot : reservations) {
			if (!slot.isValid()) {
				return false;
			}
			amount += slot.item.getAmount();
		}
		return amount >= requiredLogs;
	}

	private static void releaseReservations(List<SettlementStoragePickupSlot> reservations) {
		for (SettlementStoragePickupSlot slot : reservations) {
			if (!slot.isRemoved()) {
				slot.remove();
			}
		}
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		if (!(worker instanceof HumanMob)) {
			return null;
		}

		HumanMob human = (HumanMob)worker;
		return handler
				.setJobHandler(CharcoalProductionLevelJob.class, foundJob -> getJobSequence(human, foundJob))
				.setPredicate(
						() -> !human.isOnStrike()
								&& !human.hasCompletedMission()
								&& (!human.isSettler() || human.isSettlerWithinSettlement())
				);
	}
}
