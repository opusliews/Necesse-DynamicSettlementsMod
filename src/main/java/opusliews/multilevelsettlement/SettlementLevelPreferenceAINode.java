package opusliews.multilevelsettlement;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.decorators.MoveTaskAINode;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;

public class SettlementLevelPreferenceAINode extends MoveTaskAINode {
	public enum Mode {
		RECREATION,
		IDLE
	}

	private final Mode mode;
	private SettlementCrossLevelRoute route;
	private long nextRouteSearchTime;

	public SettlementLevelPreferenceAINode(Mode mode) {
		this.mode = mode;
	}

	@Override
	protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
	}

	@Override
	public void init(Mob mob, Blackboard blackboard) {
	}

	@Override
	public AINodeResult tickNode(Mob mob, Blackboard blackboard) {
		if (!(mob instanceof HumanMob)) return failAndClear(mob, blackboard);
		HumanMob human = (HumanMob)mob;
		if (!shouldHandle(human)) return failAndClear(mob, blackboard);

		SettlementLevelPreference preference = SettlementLevelPreferenceSystem.getPreference(human);
		SettlementLevelType preferredType = getPreferredType(preference);
		if (preferredType == null) return failAndClear(mob, blackboard);

		if (human.getLevel() == null || !human.getLevel().isServer()) return failAndClear(mob, blackboard);
		if (!human.isSettler() || !human.isSettlerWithinSettlement()) return failAndClear(mob, blackboard);

		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return failAndClear(mob, blackboard);
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return failAndClear(mob, blackboard);

		SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
		if (currentType == null || currentType == preferredType) return failAndClear(mob, blackboard);

		if (route != null && route.ladder != null) {
			int ladderX = route.ladder.getTileX(currentType);
			int ladderY = route.ladder.getTileY(currentType);
			if (human.getTileX() == ladderX && human.getTileY() == ladderY) {
				if (SettlementLadderSystem.transitionMob(human, route.ladder, preferredType)) {
					if (Logging.logEnabled) Logging.logMessage("[LevelPreference] " + mode + " transition completed settler=" + human.getUniqueID() + " preference=" + preference + " destination=" + preferredType + " via=" + route.ladder);
					route = null;
					nextRouteSearchTime = human.getTime() + 2000L;
					return AINodeResult.SUCCESS;
				}
				if (Logging.logEnabled) Logging.logMessage("[LevelPreference] " + mode + " transition failed at ladder settler=" + human.getUniqueID() + " preference=" + preference + " route=" + route);
				route = null;
				nextRouteSearchTime = human.getTime() + 5000L;
				return AINodeResult.FAILURE;
			}

			if (blackboard.mover.isCurrentlyMovingFor(this)) return AINodeResult.RUNNING;
			route = null;
		}

		if (human.getTime() < nextRouteSearchTime) return AINodeResult.FAILURE;
		nextRouteSearchTime = human.getTime() + 5000L;
		route = SettlementCrossLevelRouting.findBestTransitionRoute(human, domain, preferredType);
		if (route == null || route.ladder == null) {
			if (Logging.logEnabled) Logging.logMessage("[LevelPreference] " + mode + " preferred-level move unavailable settler=" + human.getUniqueID() + " current=" + currentType + " preferred=" + preferredType);
			route = null;
			return AINodeResult.FAILURE;
		}

		int ladderX = route.ladder.getTileX(currentType);
		int ladderY = route.ladder.getTileY(currentType);
		if (Logging.logEnabled) Logging.logMessage("[LevelPreference] " + mode + " moving settler=" + human.getUniqueID() + " current=" + currentType + " preferred=" + preferredType + " ladder=" + ladderX + "," + ladderY + " route=" + route);
		return moveToTileTask(ladderX, ladderY, null, (java.util.function.Function<MoveToTileAITask.AIPathResult, AINodeResult>)(path -> {
			boolean moving = path.moveIfWithin(-1, 0, null);
			if (!moving) {
				if (Logging.logEnabled) Logging.logMessage("[LevelPreference] " + mode + " pathfinder could not move settler=" + human.getUniqueID() + " to ladder=" + ladderX + "," + ladderY);
				route = null;
				nextRouteSearchTime = human.getTime() + 5000L;
				return AINodeResult.FAILURE;
			}
			return AINodeResult.RUNNING;
		}));
	}

	@Override
	public AINodeResult onTaskFailed(Mob mob, Blackboard blackboard) {
		if (Logging.logEnabled && mob != null) Logging.logMessage("[LevelPreference] " + mode + " path task failed settler=" + mob.getUniqueID());
		route = null;
		nextRouteSearchTime = mob == null ? 0L : mob.getTime() + 5000L;
		return AINodeResult.FAILURE;
	}

	@Override
	protected void onInterruptRunning(Mob mob, Blackboard blackboard) {
		super.onInterruptRunning(mob, blackboard);
		route = null;
	}

	private boolean shouldHandle(HumanMob human) {
		if (human == null || human.hasCommandOrders() || human.getCurrentMission() != null) return false;
		if (human.isOnStrike() || human.isHiding || human.isDowned() || human.isTrapped()) return false;
		if (human.hasActiveJob()) return false;
		if (mode == Mode.RECREATION) return human.wantsToDoRecreation();
		return !human.wantsToDoRecreation();
	}

	private SettlementLevelType getPreferredType(SettlementLevelPreference preference) {
		if (preference == SettlementLevelPreference.SURFACE) return SettlementLevelType.SURFACE;
		if (preference == SettlementLevelPreference.CAVE) return SettlementLevelType.CAVE;
		return null;
	}

	private AINodeResult failAndClear(Mob mob, Blackboard blackboard) {
		if (route != null || hasTask()) {
			route = null;
			clearTask();
			if (blackboard != null && blackboard.mover.isCurrentlyMovingFor(this)) blackboard.mover.stopMoving(mob);
		}
		return AINodeResult.FAILURE;
	}
}
