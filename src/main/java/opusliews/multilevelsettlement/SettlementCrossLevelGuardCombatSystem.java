package opusliews.multilevelsettlement;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.event.AIEvent;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanAngerTargetAINode;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.breaching.WarningBellSystem;
import opusliews.guard.GuardFatigueSystem;
import opusliews.guard.GuardDutySystem;
import opusliews.guard.GuardLevelAssignmentSystem;
import opusliews.guard.GuardNeedsSystem;
import opusliews.logging.Logging;
import opusliews.sleep.SleepWarningSystem;

/**
 * Bridges guard combat between connected settlement levels.
 *
 * Cross-level targets are never handed to vanilla chaser/pathfinding logic. The guard first
 * walks to a designated cave-access ladder, transitions, and only then exposes the target to
 * the normal item-attacker chaser on the target's physical level.
 */
public final class SettlementCrossLevelGuardCombatSystem {
	private static final long threatScanCooldownMs = 750L;
	private static final long routeRetryCooldownMs = 3000L;
	private static final Map<GuardHumanMob, CombatState> states = Collections.synchronizedMap(new WeakHashMap<>());

	private SettlementCrossLevelGuardCombatSystem() {
	}

	public static AINodeResult tick(GuardHumanMob guard, Blackboard blackboard, AINode travelNode) {
		if (guard == null || blackboard == null || travelNode == null || guard.getLevel() == null || !guard.getLevel().isServer()) {
			return AINodeResult.FAILURE;
		}
		if (!guard.isSettler() || guard.hasCommandOrders() || SleepWarningSystem.hasWakeAssignment(guard)) {
			clear(guard, blackboard, "guard unavailable for autonomous cross-level combat");
			return AINodeResult.FAILURE;
		}

		ServerSettlementData settlement = guard.getSettlerSettlementServerData();
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (settlement == null || domain == null) {
			clear(guard, blackboard, "settlement domain unavailable");
			return AINodeResult.FAILURE;
		}

		CombatState state = getState(guard);
		state.travelNode = travelNode;
		long now = guard.getTime();

		SettlementLevelType currentType = domain.getLevelType(guard.getLevel().getIdentifier());
		SettlementLevelType assignedType = GuardLevelAssignmentSystem.getAssignedLevelType(guard);
		if (currentType != null && currentType != assignedType && GuardDutySystem.shouldBeOnGuardLevel(guard) && state.target == null) {
			return tickDutyRelocation(guard, blackboard, travelNode, domain, state, currentType, assignedType, now);
		}
		if (state.target != null && !isTargetStillRelevant(guard, domain, state)) {
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelGuardCombat] Target no longer relevant guard=" + guard.getUniqueID() + " target=" + describeMob(state.target) + " source=" + state.source);
			clearStateOnly(guard, blackboard, state);
		}

		if (state.target == null) {
			if (now < state.nextThreatScanTime) return AINodeResult.FAILURE;
			state.nextThreatScanTime = now + threatScanCooldownMs;
			ThreatSelection selection = findThreat(guard, domain);
			if (selection == null) return AINodeResult.FAILURE;
			state.target = selection.target;
			state.source = selection.source;
			state.targetType = domain.getLevelType(selection.target.getLevel().getIdentifier());
			state.route = null;
			state.pathTask = null;
			state.nextRouteSearchTime = 0L;
			if (selection.emergency) {
				GuardNeedsSystem.interruptBreak(guard);
				GuardFatigueSystem.markRestCombat(guard, "cross-level emergency");
			}
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelGuardCombat] Selected cross-level threat guard=" + guard.getUniqueID() + " target=" + describeMob(state.target) + " source=" + state.source);
		}

		Mob target = state.target;
		if (target == null || target.getLevel() == null) {
			clearStateOnly(guard, blackboard, state);
			return AINodeResult.FAILURE;
		}

		if (guard.isSamePlace(target)) {
			state.route = null;
			state.pathTask = null;
			blackboard.put("currentTarget", target);
			return AINodeResult.SUCCESS;
		}

		currentType = domain.getLevelType(guard.getLevel().getIdentifier());
		SettlementLevelType targetType = domain.getLevelType(target.getLevel().getIdentifier());
		if (currentType == null || targetType == null || currentType == targetType) {
			clearStateOnly(guard, blackboard, state);
			return AINodeResult.FAILURE;
		}
		if (state.targetType != targetType) {
			state.targetType = targetType;
			state.route = null;
			state.pathTask = null;
			state.nextRouteSearchTime = 0L;
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelGuardCombat] Target changed settlement level while travelling guard=" + guard.getUniqueID() + " target=" + describeMob(target) + " newTargetLevel=" + targetType);
		}

		if (state.route == null) {
			if (now < state.nextRouteSearchTime) return AINodeResult.FAILURE;
			state.nextRouteSearchTime = now + routeRetryCooldownMs;
			state.route = SettlementCrossLevelRouting.findBestTransitionRoute(guard, domain, targetType);
			state.pathTask = null;
			if (state.route == null || state.route.ladder == null) {
				state.route = null;
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelGuardCombat] No designated ladder route guard=" + guard.getUniqueID() + " target=" + describeMob(target) + " targetLevel=" + targetType);
				return AINodeResult.FAILURE;
			}
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelGuardCombat] Routing guard=" + guard.getUniqueID() + " target=" + describeMob(target) + " route=" + state.route);
		}

		if (SettlementLadderSystem.isMobAtOrAdjacentLadder(guard, state.route.ladder, currentType)) {
			if (blackboard.mover.isCurrentlyMovingFor(travelNode)) blackboard.mover.stopMoving(guard);
			boolean transitioned = SettlementLadderSystem.transitionMob(guard, state.route.ladder, targetType);
			state.pathTask = null;
			state.route = null;
			if (transitioned) {
				blackboard.put("currentTarget", null);
				blackboard.put("chaserTarget", null);
				blackboard.submitEvent("resetPathTime", new AIEvent());
				blackboard.submitEvent("resetTarget", new AIEvent());
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelGuardCombat] Ladder transition completed guard=" + guard.getUniqueID() + " target=" + describeMob(target) + " targetLevel=" + targetType);
				return AINodeResult.RUNNING;
			}
			state.nextRouteSearchTime = now + routeRetryCooldownMs;
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelGuardCombat] Ladder transition FAILED guard=" + guard.getUniqueID() + " target=" + describeMob(target) + "; route will be re-evaluated");
			return AINodeResult.FAILURE;
		}

		if (state.pathTask != null && state.pathTask.isComplete()) {
			try {
				AINodeResult result = state.pathTask.runComplete();
				state.pathTask = null;
				if (result == AINodeResult.FAILURE) {
					state.route = null;
					state.nextRouteSearchTime = now + routeRetryCooldownMs;
					if (Logging.logEnabled) Logging.logMessage("[CrossLevelGuardCombat] Ladder path failed guard=" + guard.getUniqueID() + " target=" + describeMob(target));
					return AINodeResult.FAILURE;
				}
			} catch (Exception e) {
				state.pathTask = null;
				state.route = null;
				state.nextRouteSearchTime = now + routeRetryCooldownMs;
				if (Logging.logEnabled) Logging.logMessage("[CrossLevelGuardCombat] Ladder path exception guard=" + guard.getUniqueID() + " target=" + describeMob(target) + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
				return AINodeResult.FAILURE;
			}
		}

		if (blackboard.mover.isCurrentlyMovingFor(travelNode)) return AINodeResult.RUNNING;
		if (blackboard.mover.isMoving()) blackboard.mover.stopMoving(guard);
		if (state.pathTask == null && state.route != null) {
			int ladderX = state.route.ladder.getTileX(currentType);
			int ladderY = state.route.ladder.getTileY(currentType);
			state.pathTask = blackboard.mover.moveToTileTask(travelNode, ladderX, ladderY, null, path -> {
				boolean moving = path.moveIfWithin(-1, 0, null);
				return moving ? AINodeResult.RUNNING : AINodeResult.FAILURE;
			});
		}
		return AINodeResult.RUNNING;
	}

	private static AINodeResult tickDutyRelocation(GuardHumanMob guard, Blackboard blackboard, AINode travelNode, SettlementLevelDomain domain, CombatState state, SettlementLevelType currentType, SettlementLevelType assignedType, long now) {
		if (state.route == null || state.targetType != assignedType) {
			if (now < state.nextRouteSearchTime) return AINodeResult.FAILURE;
			state.nextRouteSearchTime = now + routeRetryCooldownMs;
			state.route = SettlementCrossLevelRouting.findBestTransitionRoute(guard, domain, assignedType);
			state.targetType = assignedType;
			state.pathTask = null;
			if (state.route == null || state.route.ladder == null) {
				state.route = null;
				if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Night guard cannot reach assigned guard level guard=" + guard.getUniqueID() + " current=" + currentType + " assigned=" + assignedType);
				return AINodeResult.FAILURE;
			}
			if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Night guard relocating to assigned guard level guard=" + guard.getUniqueID() + " current=" + currentType + " assigned=" + assignedType + " route=" + state.route);
		}

		if (SettlementLadderSystem.isMobAtOrAdjacentLadder(guard, state.route.ladder, currentType)) {
			if (blackboard.mover.isCurrentlyMovingFor(travelNode)) blackboard.mover.stopMoving(guard);
			boolean transitioned = SettlementLadderSystem.transitionMob(guard, state.route.ladder, assignedType);
			state.pathTask = null;
			state.route = null;
			state.targetType = null;
			if (transitioned) {
				blackboard.submitEvent("resetPathTime", new AIEvent());
				blackboard.submitEvent("resetTarget", new AIEvent());
				if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Night guard reached assigned guard level guard=" + guard.getUniqueID() + " assigned=" + assignedType);
				return AINodeResult.RUNNING;
			}
			state.nextRouteSearchTime = now + routeRetryCooldownMs;
			if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Night guard ladder transition FAILED guard=" + guard.getUniqueID() + " assigned=" + assignedType);
			return AINodeResult.FAILURE;
		}

		if (state.pathTask != null && state.pathTask.isComplete()) {
			try {
				AINodeResult result = state.pathTask.runComplete();
				state.pathTask = null;
				if (result == AINodeResult.FAILURE) {
					state.route = null;
					state.nextRouteSearchTime = now + routeRetryCooldownMs;
					if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Night guard path to assigned level failed guard=" + guard.getUniqueID() + " assigned=" + assignedType);
					return AINodeResult.FAILURE;
				}
			}
			catch (Exception e) {
				state.pathTask = null;
				state.route = null;
				state.nextRouteSearchTime = now + routeRetryCooldownMs;
				if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Night guard path exception guard=" + guard.getUniqueID() + " assigned=" + assignedType + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
				return AINodeResult.FAILURE;
			}
		}

		if (blackboard.mover.isCurrentlyMovingFor(travelNode)) return AINodeResult.RUNNING;
		if (blackboard.mover.isMoving()) blackboard.mover.stopMoving(guard);
		if (state.pathTask == null && state.route != null) {
			int ladderX = state.route.ladder.getTileX(currentType);
			int ladderY = state.route.ladder.getTileY(currentType);
			state.pathTask = blackboard.mover.moveToTileTask(travelNode, ladderX, ladderY, null, path -> {
				boolean moving = path.moveIfWithin(-1, 0, null);
				return moving ? AINodeResult.RUNNING : AINodeResult.FAILURE;
			});
		}
		return AINodeResult.RUNNING;
	}

	public static boolean isResponding(GuardHumanMob guard) {
		if (guard == null) return false;
		CombatState state = states.get(guard);
		return state != null
				&& state.target != null
				&& !state.target.removed()
				&& state.target.getHealth() > 0
				&& (guard.isSamePlace(state.target) || state.route != null || state.pathTask != null);
	}

	public static Mob getTarget(GuardHumanMob guard) {
		CombatState state = guard == null ? null : states.get(guard);
		return state == null ? null : state.target;
	}

	public static void clear(GuardHumanMob guard, Blackboard blackboard, String reason) {
		if (guard == null) return;
		CombatState state = states.remove(guard);
		if (state == null) return;
		if (blackboard != null) {
			clearTargetFromBlackboard(blackboard, state.target);
			if (state.travelNode != null && blackboard.mover != null && blackboard.mover.isCurrentlyMovingFor(state.travelNode)) {
				blackboard.mover.stopMoving(guard);
			}
		}
		if (Logging.logEnabled) Logging.logMessage("[CrossLevelGuardCombat] Cleared response guard=" + guard.getUniqueID() + " reason=" + reason);
	}

	private static ThreatSelection findThreat(GuardHumanMob guard, SettlementLevelDomain domain) {
		SettlementLevelType currentType = domain.getLevelType(guard.getLevel().getIdentifier());
		if (currentType == null) return null;
		SettlementLevelType assignedType = GuardLevelAssignmentSystem.getAssignedLevelType(guard);
		if (assignedType == currentType) return null;
		Level assignedLevel = domain.getLoadedLevel(assignedType);
		if (assignedLevel == null) return null;

		Mob directAttacker = GuardFatigueSystem.getDirectRestAttacker(guard);
		if (isCrossLevelCandidate(guard, domain, directAttacker)) {
			return new ThreatSelection(directAttacker, ThreatSource.DIRECT_ATTACKER, false);
		}

		Mob angerTarget = findAngerTarget(guard, domain);
		if (angerTarget != null) return new ThreatSelection(angerTarget, ThreatSource.ANGER, false);

		WarningBellSystem.CrossLevelAlert alert = WarningBellSystem.getCrossLevelAlert(assignedLevel, domain.getSettlementUniqueID());
		if (alert != null && isCrossLevelCandidate(guard, domain, alert.target)) {
			if (alert.emergency) {
				if (!GuardFatigueSystem.canRespondToEmergency(guard)) return null;
			}
			else if (GuardFatigueSystem.isScheduledRestPeriod(guard)) {
				return null;
			}
			return new ThreatSelection(alert.target, ThreatSource.WARNING_BELL, alert.emergency);
		}

		ServerSettlementData settlement = domain.getSettlement();
		if (settlement.networkData.isRaidActive() && assignedType == SettlementLevelType.SURFACE) {
			Mob raidTarget = findRaidTarget(guard, domain, assignedLevel);
			if (raidTarget != null) return new ThreatSelection(raidTarget, ThreatSource.RAID, false);
		}

		return null;
	}

	private static Mob findAngerTarget(GuardHumanMob guard, SettlementLevelDomain domain) {
		if (guard.ai == null || guard.ai.blackboard == null) return null;
		HumanAngerTargetAINode anger = guard.ai.blackboard.getObject(HumanAngerTargetAINode.class, "humanAngerHandler");
		if (anger == null || anger.enemies == null || anger.enemies.isEmpty()) return null;
		Mob best = null;
		long bestDistance = Long.MAX_VALUE;
		for (Object value : anger.enemies) {
			if (!(value instanceof Mob)) continue;
			Mob target = (Mob)value;
			if (!isCrossLevelCandidate(guard, domain, target)) continue;
			long dx = target.getTileX() - guard.getTileX();
			long dy = target.getTileY() - guard.getTileY();
			long distance = dx * dx + dy * dy;
			if (best == null || distance < bestDistance) {
				best = target;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static Mob findRaidTarget(GuardHumanMob guard, SettlementLevelDomain domain, Level targetLevel) {
		java.util.List<SettlementLadderLink> links = SettlementLadderSystem.getValidLinks(domain, false);
		if (links.isEmpty()) return null;
		Mob best = null;
		long bestDistance = Long.MAX_VALUE;
		for (Mob target : targetLevel.entityManager.mobs) {
			if (!isCrossLevelCandidate(guard, domain, target)) continue;
			if (!domain.isTileWithinBounds(targetLevel.getIdentifier(), target.getTileX(), target.getTileY())) continue;
			long nearest = Long.MAX_VALUE;
			for (SettlementLadderLink link : links) {
				long dx = link.getTileX(SettlementLevelType.SURFACE) - target.getTileX();
				long dy = link.getTileY(SettlementLevelType.SURFACE) - target.getTileY();
				nearest = Math.min(nearest, dx * dx + dy * dy);
			}
			if (best == null || nearest < bestDistance) {
				best = target;
				bestDistance = nearest;
			}
		}
		return best;
	}

	private static boolean isTargetStillRelevant(GuardHumanMob guard, SettlementLevelDomain domain, CombatState state) {
		Mob target = state.target;
		if (!isCandidate(guard, domain, target)) return false;
		if (domain.getLevelType(target.getLevel().getIdentifier()) != GuardLevelAssignmentSystem.getAssignedLevelType(guard)) return false;
		if (guard.isSamePlace(target)) return true;
		if (state.source == ThreatSource.DIRECT_ATTACKER) return GuardFatigueSystem.getDirectRestAttacker(guard) == target;
		if (state.source == ThreatSource.ANGER) {
			HumanAngerTargetAINode anger = guard.ai == null ? null : guard.ai.blackboard.getObject(HumanAngerTargetAINode.class, "humanAngerHandler");
			return anger != null && anger.enemies != null && anger.enemies.contains(target);
		}
		if (state.source == ThreatSource.WARNING_BELL) return true;
		if (state.source == ThreatSource.RAID) return domain.getLevelType(target.getLevel().getIdentifier()) == SettlementLevelType.SURFACE;
		return false;
	}

	private static boolean isCrossLevelCandidate(GuardHumanMob guard, SettlementLevelDomain domain, Mob target) {
		if (!isCandidate(guard, domain, target) || guard.isSamePlace(target)) return false;
		return domain.getLevelType(target.getLevel().getIdentifier()) == GuardLevelAssignmentSystem.getAssignedLevelType(guard);
	}

	private static boolean isCandidate(GuardHumanMob guard, SettlementLevelDomain domain, Mob target) {
		if (target == null || target.getLevel() == null || target.removed() || target.getHealth() <= 0) return false;
		if (domain.getLevelType(target.getLevel().getIdentifier()) == null) return false;
		if (!target.canTakeDamage() || !target.canBeHit(guard) || !guard.filterHumanTargets().test(target)) return false;
		return domain.isTileWithinBounds(target.getLevel().getIdentifier(), target.getTileX(), target.getTileY());
	}

	private static CombatState getState(GuardHumanMob guard) {
		synchronized (states) {
			return states.computeIfAbsent(guard, ignored -> new CombatState());
		}
	}

	private static void clearStateOnly(GuardHumanMob guard, Blackboard blackboard, CombatState state) {
		Mob target = state.target;
		state.target = null;
		state.source = null;
		state.targetType = null;
		state.route = null;
		state.pathTask = null;
		state.nextRouteSearchTime = 0L;
		clearTargetFromBlackboard(blackboard, target);
		if (guard != null && blackboard != null && state.travelNode != null && blackboard.mover != null && blackboard.mover.isCurrentlyMovingFor(state.travelNode)) {
			blackboard.mover.stopMoving(guard);
		}
	}

	private static void clearTargetFromBlackboard(Blackboard blackboard, Mob target) {
		if (blackboard == null || target == null) return;
		if (blackboard.getObject(Mob.class, "currentTarget") == target) blackboard.put("currentTarget", null);
		if (blackboard.getObject(Mob.class, "chaserTarget") == target) blackboard.put("chaserTarget", null);
	}

	private static String describeMob(Mob mob) {
		if (mob == null) return "null";
		return mob.getStringID() + "#" + mob.getUniqueID() + " level=" + (mob.getLevel() == null ? "null" : mob.getLevel().getIdentifier()) + " tile=" + mob.getTileX() + "," + mob.getTileY();
	}

	private enum ThreatSource {
		DIRECT_ATTACKER,
		ANGER,
		WARNING_BELL,
		RAID
	}

	private static final class ThreatSelection {
		final Mob target;
		final ThreatSource source;
		final boolean emergency;

		ThreatSelection(Mob target, ThreatSource source, boolean emergency) {
			this.target = target;
			this.source = source;
			this.emergency = emergency;
		}
	}

	private static final class CombatState {
		Mob target;
		ThreatSource source;
		SettlementLevelType targetType;
		SettlementCrossLevelRoute route;
		MoveToTileAITask pathTask;
		AINode travelNode;
		long nextThreatScanTime;
		long nextRouteSearchTime;
	}
}
