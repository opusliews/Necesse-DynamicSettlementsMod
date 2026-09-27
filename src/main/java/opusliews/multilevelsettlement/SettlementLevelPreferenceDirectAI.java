package opusliews.multilevelsettlement;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.CompositeTypedAINode;
import necesse.entity.mobs.ai.behaviourTree.DynamicSettlementsAIReset;
import necesse.entity.mobs.ai.behaviourTree.event.AIEvent;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.ai.behaviourTree.trees.HumanAI;
import necesse.entity.mobs.job.JobWorkerChatterHandler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;

public final class SettlementLevelPreferenceDirectAI {
	private static final Map<HumanMob, State> states = Collections.synchronizedMap(new WeakHashMap<>());

	private SettlementLevelPreferenceDirectAI() {
	}

	public static AINodeResult tickIfActive(CompositeTypedAINode<?, ?> node, Mob mob, Blackboard<?> blackboard) {
		if (!(node instanceof HumanAI<?>) || !(mob instanceof HumanMob) || blackboard == null) return null;
		HumanMob human = (HumanMob)mob;
		if (human.getLevel() == null || !human.getLevel().isServer()) return null;

		SettlementLevelPreference preference = SettlementLevelPreferenceSystem.getPreference(human);
		SettlementLevelType preferredType = getPreferredType(preference);
		State state = states.get(human);

		if (!canTakeControl(human, blackboard, preferredType)) {
			if (state != null) clearState(human, blackboard.mover, state, "became-ineligible");
			return null;
		}

		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) {
			if (state != null) clearState(human, blackboard.mover, state, "missing-settlement");
			return null;
		}
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) {
			if (state != null) clearState(human, blackboard.mover, state, "missing-domain");
			return null;
		}

		SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
		if (currentType == null || currentType == preferredType) {
			if (state != null) clearState(human, blackboard.mover, state, "already-preferred-level");
			return null;
		}

		if (state == null || state.preferredType != preferredType) {
			if (state != null) clearState(human, blackboard.mover, state, "preference-changed");
			state = new State(human, blackboard, preferredType);
			states.put(human, state);
		}

		if (state.route == null) {
			if (human.getTime() < state.nextRouteSearchTime) return null;
			state.nextRouteSearchTime = human.getTime() + 5000L;
			state.route = SettlementCrossLevelRouting.findBestTransitionRoute(human, domain, preferredType);
			if (state.route == null || state.route.ladder == null) {
				if (Logging.logEnabled) Logging.logMessage("[LevelPreference] DIRECT preferred-level move unavailable settler=" + human.getUniqueID() + " current=" + currentType + " preferred=" + preferredType);
				state.route = null;
				return null;
			}
			prepareVanillaAIForTakeover(node, human, blackboard);
			if (Logging.logEnabled) Logging.logMessage("[LevelPreference] DIRECT taking control settler=" + human.getUniqueID() + " current=" + currentType + " preferred=" + preferredType + " route=" + state.route);
		}

		int ladderX = state.route.ladder.getTileX(currentType);
		int ladderY = state.route.ladder.getTileY(currentType);
		if (human.getTileX() == ladderX && human.getTileY() == ladderY) {
			boolean transitioned = SettlementLadderSystem.transitionMob(human, state.route.ladder, preferredType);
			if (Logging.logEnabled) Logging.logMessage("[LevelPreference] DIRECT transition " + (transitioned ? "completed" : "failed") + " settler=" + human.getUniqueID() + " preferred=" + preferredType + " ladder=" + ladderX + "," + ladderY);
			clearState(human, blackboard.mover, state, transitioned ? "transition-complete" : "transition-failed");
			if (transitioned) blackboard.submitEvent("wanderNow", new AIEvent());
			return AINodeResult.RUNNING;
		}

		if (state.pathTask != null && state.pathTask.isComplete()) {
			try {
				AINodeResult result = state.pathTask.runComplete();
				state.pathTask = null;
				if (result == AINodeResult.FAILURE) {
					if (Logging.logEnabled) Logging.logMessage("[LevelPreference] DIRECT path task failed settler=" + human.getUniqueID() + " ladder=" + ladderX + "," + ladderY);
					state.route = null;
					state.nextRouteSearchTime = human.getTime() + 5000L;
					return AINodeResult.RUNNING;
				}
			}
			catch (Exception e) {
				if (Logging.logEnabled) Logging.logMessage("[LevelPreference] DIRECT path task exception settler=" + human.getUniqueID() + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
				state.pathTask = null;
				state.route = null;
				state.nextRouteSearchTime = human.getTime() + 5000L;
				return AINodeResult.RUNNING;
			}
		}

		if (blackboard.mover.isCurrentlyMovingFor(state.node)) return AINodeResult.RUNNING;

		if (state.pathTask == null) {
			if (Logging.logEnabled) Logging.logMessage("[LevelPreference] DIRECT pathing settler=" + human.getUniqueID() + " to ladder=" + ladderX + "," + ladderY + " preferred=" + preferredType);
			state.pathTask = blackboard.mover.moveToTileTask(state.node, ladderX, ladderY, null, path -> {
				boolean moving = path.moveIfWithin(-1, 0, null);
				if (!moving && Logging.logEnabled) Logging.logMessage("[LevelPreference] DIRECT pathfinder could not move settler=" + human.getUniqueID() + " to ladder=" + ladderX + "," + ladderY);
				return moving ? AINodeResult.RUNNING : AINodeResult.FAILURE;
			});
		}

		return AINodeResult.RUNNING;
	}

	private static boolean canTakeControl(HumanMob human, Blackboard<?> blackboard, SettlementLevelType preferredType) {
		if (preferredType == null) return false;
		if (SettlementCrossLevelSleepSystem.shouldYieldPreferenceForSleep(human)) return false;
		if (!human.isSettler() || !human.isSettlerWithinSettlement()) return false;
		if (human.hasCommandOrders() || human.getCurrentMission() != null) return false;
		if (human.isOnStrike() || human.isHiding || human.isDowned() || human.isTrapped()) return false;
		if (human.isBeingInteractedWith() || human.hasActiveJob() || human.isInCombat()) return false;
		Mob target = blackboard.getObject(Mob.class, "chaserTarget");
		return target == null;
	}

	private static void prepareVanillaAIForTakeover(CompositeTypedAINode<?, ?> node, HumanMob human, Blackboard<?> blackboard) {
		if (node == null || human == null || blackboard == null) return;

		JobWorkerChatterHandler chatter = human.getCurrentChatterHandler();
		if (chatter != null && chatter.isInConversation()) {
			JobWorkerChatterHandler other = chatter.getCurrentlyInteractingWith();
			if (other != null) other.endConversation(false, false);
			chatter.endConversation(false, false);
		}

		if (blackboard.mover.isMoving()) blackboard.mover.stopMoving(human);
		DynamicSettlementsAIReset.reset(node, human, blackboard);
		if (Logging.logEnabled) Logging.logMessage("[LevelPreference] DIRECT reset vanilla AI before cross-level relocation settler=" + human.getUniqueID());
	}

	private static SettlementLevelType getPreferredType(SettlementLevelPreference preference) {
		if (preference == SettlementLevelPreference.SURFACE) return SettlementLevelType.SURFACE;
		if (preference == SettlementLevelPreference.CAVE) return SettlementLevelType.CAVE;
		return null;
	}

	private static void clearState(HumanMob human, AIMover mover, State state, String reason) {
		if (state == null) return;
		if (mover != null && mover.isCurrentlyMovingFor(state.node)) mover.stopMoving(human);
		state.pathTask = null;
		state.route = null;
		states.remove(human);
		if (Logging.logEnabled) Logging.logMessage("[LevelPreference] DIRECT released control settler=" + human.getUniqueID() + " reason=" + reason);
	}

	private static final class State {
		final DirectNode node;
		final SettlementLevelType preferredType;
		SettlementCrossLevelRoute route;
		MoveToTileAITask pathTask;
		long nextRouteSearchTime;

		State(HumanMob human, Blackboard<?> blackboard, SettlementLevelType preferredType) {
			this.node = new DirectNode();
			this.preferredType = preferredType;
			this.node.makeRoot(human, (Blackboard)blackboard);
		}
	}

	private static final class DirectNode extends AINode<HumanMob> {
		@Override
		protected void onRootSet(AINode<HumanMob> root, HumanMob mob, Blackboard<HumanMob> blackboard) {
		}

		@Override
		public void init(HumanMob mob, Blackboard<HumanMob> blackboard) {
		}

		@Override
		public AINodeResult tick(HumanMob mob, Blackboard<HumanMob> blackboard) {
			return AINodeResult.RUNNING;
		}
	}
}
