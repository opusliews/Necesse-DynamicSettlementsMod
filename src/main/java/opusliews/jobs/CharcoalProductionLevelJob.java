package opusliews.jobs;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.entity.mobs.job.LinkedListJobSequence;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import opusliews.charcoal.CharcoalProductionZone;

public class CharcoalProductionLevelJob extends TileLevelJob {
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
				&& CharcoalProductionZone.isValidCandidate(getLevel(), tileX, tileY, this);
	}

	private TileActiveJob getActiveJob(EntityJobWorker worker, JobTypeHandler.TypePriority priority) {
		return new TileActiveJob(worker, priority, tileX, tileY) {
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
			}

			@Override
			public boolean isValid(boolean isCurrent) {
				return !CharcoalProductionLevelJob.this.isRemoved()
						&& CharcoalProductionLevelJob.this.isValid()
						&& CharcoalProductionLevelJob.this.reservable.isAvailable(worker.getMobWorker());
			}

			@Override
			public ActiveJobResult perform() {
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

		LinkedListJobSequence sequence = new LinkedListJobSequence(
				new LocalMessage("activities", "charcoalproduction"),
				false
		);
		sequence.add(job.getActiveJob(worker, foundJob.priority));
		return sequence;
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
