package opusliews.jobs;

import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementCrossLevelRoute;
import opusliews.multilevelsettlement.SettlementLadderLink;
import opusliews.multilevelsettlement.SettlementLadderSystem;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

/** Moves a hauling worker through the designated ladder while preserving the same JobSequence. */
public class CrossLevelHaulingTransitionActiveJob extends TileActiveJob {
	private final SettlementCrossLevelRoute route;
	private final SettlementInventory destination;
	private final SettlementLevelType sourceType;
	private final SettlementLevelType destinationType;

	public CrossLevelHaulingTransitionActiveJob(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			SettlementCrossLevelRoute route,
			SettlementInventory destination,
			SettlementLevelType sourceType
	) {
		super(worker, priority, route.ladder.getTileX(sourceType), route.ladder.getTileY(sourceType));
		this.route = route;
		this.destination = destination;
		this.sourceType = sourceType;
		this.destinationType = sourceType == SettlementLevelType.SURFACE ? SettlementLevelType.CAVE : SettlementLevelType.SURFACE;
	}

	@Override
	public JobMoveToTile getMoveToTile(JobMoveToTile lastTile) {
		return new JobMoveToTile(tileX, tileY, true);
	}

	@Override
	public boolean isAt(JobMoveToTile moveToTile) {
		if (!(worker.getMobWorker() instanceof HumanMob)) return false;
		return SettlementLadderSystem.isMobAtOrAdjacentLadder((HumanMob)worker.getMobWorker(), route.ladder, sourceType);
	}

	@Override
	public void tick(boolean isCurrent, boolean isMovingTo) {
	}

	@Override
	public boolean isValid(boolean isCurrent) {
		if (!(worker.getMobWorker() instanceof HumanMob) || route == null || route.ladder == null || destination == null) return false;
		HumanMob human = (HumanMob)worker.getMobWorker();
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(human.getSettlerSettlementServerData());
		if (domain == null || human.getLevel() == null) return false;
		if (domain.getLevelType(human.getLevel().getIdentifier()) != sourceType) return false;
		if (!destination.isStorageValid()) return false;
		for (SettlementLadderLink link : SettlementLadderSystem.getValidLinks(domain, true)) {
			if (link.equals(route.ladder)) return true;
		}
		return false;
	}

	@Override
	public ActiveJobResult perform() {
		if (!(worker.getMobWorker() instanceof HumanMob)) return ActiveJobResult.FAILED;
		HumanMob human = (HumanMob)worker.getMobWorker();
		boolean transitioned = SettlementLadderSystem.transitionMob(human, route.ladder, destinationType);
		if (Logging.logEnabled) Logging.logMessage("[CrossLevelHauling] Ladder transition " + (transitioned ? "completed" : "FAILED")
				+ " settler=" + human.getUniqueID() + " from=" + sourceType + " to=" + destinationType + " destination="
				+ destination.level.getIdentifier() + "@" + destination.tileX + "," + destination.tileY);
		return transitioned ? ActiveJobResult.FINISHED : ActiveJobResult.FAILED;
	}
}
