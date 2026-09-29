package opusliews.multilevelsettlement;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanSleepAINode;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import opusliews.logging.Logging;

public final class SettlementCrossLevelSleepSystem {
	private static final Map<HumanMob, State> states = Collections.synchronizedMap(new WeakHashMap<>());

	private SettlementCrossLevelSleepSystem() {
	}

	public static boolean shouldYieldPreferenceForSleep(HumanMob human) {
		return getSleepTarget(human) != null && shouldSleepNow(human);
	}

	public static AINodeResult tickCrossLevelSleep(HumanSleepAINode node, HumanMob human, Blackboard<?> blackboard) {
		if (node == null || human == null || blackboard == null || human.getLevel() == null || !human.getLevel().isServer()) return null;
		SleepTarget target = getSleepTarget(human);
		State state = states.get(human);

		if (target == null || !canSleepCrossLevel(human) || !shouldSleepNow(human)) {
			if (state != null) clearState(node, human, blackboard, state, "sleep-no-longer-needed");
			return null;
		}
		if (target.bedLevel.equals(human.getLevel().getIdentifier())) {
			if (state != null) clearState(node, human, blackboard, state, "already-on-bed-level");
			target.levelSettler.updateHome();
			return null;
		}

		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(target.settlement);
		if (domain == null) return null;
		SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
		SettlementLevelType bedType = domain.getLevelType(target.bedLevel);
		if (currentType == null || bedType == null || currentType == bedType) return null;

		if (state == null || state.bedType != bedType || state.bedX != target.bed.tileX || state.bedY != target.bed.tileY) {
			if (state != null) clearState(node, human, blackboard, state, "bed-target-changed");
			state = new State(bedType, target.bed.tileX, target.bed.tileY);
			states.put(human, state);
		}

		if (state.route == null) {
			if (human.getTime() < state.nextRouteSearchTime) return AINodeResult.FAILURE;
			state.nextRouteSearchTime = human.getTime() + 5000L;
			state.route = SettlementCrossLevelRouting.findBestTransitionRoute(human, domain, bedType);
			if (state.route == null || state.route.ladder == null) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelSleep] No ladder route to bed level settler=" + human.getUniqueID() + " current=" + currentType + " bedLevel=" + bedType + " bed=" + target.bed.tileX + "," + target.bed.tileY);
				state.route = null;
				return AINodeResult.FAILURE;
			}
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelSleep] Going to bed level settler=" + human.getUniqueID() + " current=" + currentType + " bedLevel=" + bedType + " bed=" + target.bed.tileX + "," + target.bed.tileY + " route=" + state.route);
		}

		int ladderX = state.route.ladder.getTileX(currentType);
		int ladderY = state.route.ladder.getTileY(currentType);
		if (human.getTileX() == ladderX && human.getTileY() == ladderY) {
			if (blackboard.mover.isCurrentlyMovingFor(node)) blackboard.mover.stopMoving(human);
			boolean transitioned = SettlementLadderSystem.transitionMob(human, state.route.ladder, bedType);
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelSleep] Bed-level transition " + (transitioned ? "completed" : "failed") + " settler=" + human.getUniqueID() + " bedLevel=" + bedType);
			clearState(node, human, blackboard, state, transitioned ? "transition-complete" : "transition-failed");
			if (transitioned) {
				target.levelSettler.updateHome();
				// Vanilla HumanSleepAINode normally waits 5-10 seconds before retrying its bed path.
				// After a cross-level transition that delay lets unrelated AI take over and wander first.
				node.ticks = 0;
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelSleep] Armed immediate vanilla bed path after transition settler=" + human.getUniqueID() + " bed=" + target.bed.tileX + "," + target.bed.tileY);
			}
			return transitioned ? AINodeResult.RUNNING : AINodeResult.FAILURE;
		}

		if (state.pathTask != null && state.pathTask.isComplete()) {
			try {
				AINodeResult result = state.pathTask.runComplete();
				state.pathTask = null;
				if (result == AINodeResult.FAILURE) {
					if (Logging.logEnabled) Logging.logMessage("[CrossLevelSleep] Path to ladder failed settler=" + human.getUniqueID() + " ladder=" + ladderX + "," + ladderY);
					state.route = null;
					state.nextRouteSearchTime = human.getTime() + 5000L;
					return AINodeResult.FAILURE;
				}
			} catch (Exception e) {
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelSleep] Path task exception settler=" + human.getUniqueID() + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
				state.pathTask = null;
				state.route = null;
				state.nextRouteSearchTime = human.getTime() + 5000L;
				return AINodeResult.FAILURE;
			}
		}

		if (blackboard.mover.isCurrentlyMovingFor(node)) return AINodeResult.RUNNING;
		if (state.pathTask == null) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelSleep] Pathing to ladder settler=" + human.getUniqueID() + " ladder=" + ladderX + "," + ladderY);
			state.pathTask = blackboard.mover.moveToTileTask(node, ladderX, ladderY, null, path -> {
				boolean moving = path.moveIfWithin(-1, 0, null);
				return moving ? AINodeResult.RUNNING : AINodeResult.FAILURE;
			});
		}
		return AINodeResult.RUNNING;
	}

	private static boolean canSleepCrossLevel(HumanMob human) {
		if (human.isVisitor() || human.isBeingInteractedWith()) return false;
		if (human.isDowned() || human.isTrapped() || human.isOnStrike()) return false;
		NetworkSettlementData settlement = human.getSettlerSettlementNetworkData();
		if (settlement != null && settlement.isRaidActive()) return false;
		return !human.hasCommandOrders() || human.isHiding;
	}

	private static boolean shouldSleepNow(HumanMob human) {
		return human != null && !human.isOnStrike() && (human.isHiding || human.getWorldEntity().isNight());
	}

	private static SleepTarget getSleepTarget(HumanMob human) {
		if (human == null || !human.isServer() || !human.isSettler()) return null;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return null;
		LevelSettler levelSettler = settlement.getSettler(human.getUniqueID());
		if (levelSettler == null) return null;
		SettlementBed bed = levelSettler.getBed();
		if (bed == null) return null;
		LevelIdentifier bedLevel = SettlementCaveBedSystem.getBedLevelIdentifier(levelSettler, bed);
		return bedLevel == null ? null : new SleepTarget(settlement, levelSettler, bed, bedLevel);
	}

	private static void clearState(HumanSleepAINode node, HumanMob human, Blackboard<?> blackboard, State state, String reason) {
		if (state == null) return;
		if (blackboard != null && blackboard.mover.isCurrentlyMovingFor(node)) blackboard.mover.stopMoving(human);
		state.pathTask = null;
		state.route = null;
		states.remove(human);
		if (Logging.logEnabled) Logging.logMessage("[CrossLevelSleep] Released sleep travel settler=" + human.getUniqueID() + " reason=" + reason);
	}

	private static final class SleepTarget {
		final ServerSettlementData settlement;
		final LevelSettler levelSettler;
		final SettlementBed bed;
		final LevelIdentifier bedLevel;

		SleepTarget(ServerSettlementData settlement, LevelSettler levelSettler, SettlementBed bed, LevelIdentifier bedLevel) {
			this.settlement = settlement;
			this.levelSettler = levelSettler;
			this.bed = bed;
			this.bedLevel = bedLevel;
		}
	}

	private static final class State {
		final SettlementLevelType bedType;
		final int bedX;
		final int bedY;
		SettlementCrossLevelRoute route;
		MoveToTileAITask pathTask;
		long nextRouteSearchTime;

		State(SettlementLevelType bedType, int bedX, int bedY) {
			this.bedType = bedType;
			this.bedX = bedX;
			this.bedY = bedY;
		}
	}
}
