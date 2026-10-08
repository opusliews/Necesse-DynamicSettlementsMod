package opusliews.patches;

import java.awt.Rectangle;
import java.util.List;

import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.BehaviourTreeAI;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.decorators.MoveTaskAINode;
import necesse.entity.mobs.ai.behaviourTree.leaves.TargetFinderAINode;
import necesse.entity.mobs.ai.behaviourTree.event.AIEvent;
import necesse.entity.mobs.ai.behaviourTree.trees.CollisionPlayerChaserWandererAI;
import necesse.entity.mobs.ai.behaviourTree.trees.PlayerChaserWandererAI;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import necesse.entity.mobs.hostile.FlyingHostileMob;
import necesse.entity.mobs.hostile.HostileMob;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.breaching.ZombieBreaching;
import opusliews.logging.Logging;
import opusliews.progression.GuideProgressionSystem;
import opusliews.multilevelsettlement.SettlementLadderLink;
import opusliews.multilevelsettlement.SettlementLadderSystem;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

@ModConstructorPatch(target = BehaviourTreeAI.class, arguments = {Mob.class, AINode.class, AIMover.class})
public class ZombieCrossLevelSmellAIPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) Mob mob, @Advice.Argument(1) AINode tree) {
		if (!isConstructorEligibleHostile(mob)) return;

		if (tree instanceof CollisionPlayerChaserWandererAI) {
			CollisionPlayerChaserWandererAI root = (CollisionPlayerChaserWandererAI)tree;
			if (root.wandererAINode != null) {
                root.addChildBefore(root.wandererAINode, new HostileCrossLevelSmellAINode(root.collisionPlayerChaserAI.targetFinderAINode));
                if (ZombieBreaching.isZombie(mob)) root.addChildBefore(root.wandererAINode, new ZombieSettlementAttractionPatch.AttractionNode());
            }
		}
		else if (tree instanceof PlayerChaserWandererAI) {
			PlayerChaserWandererAI root = (PlayerChaserWandererAI)tree;
			if (root.wandererAINode != null) {
                root.addChildBefore(root.wandererAINode, new HostileCrossLevelSmellAINode(root.playerChaserAI.targetFinderAINode));
                if (ZombieBreaching.isZombie(mob)) root.addChildBefore(root.wandererAINode, new ZombieSettlementAttractionPatch.AttractionNode());
            }
		}
	}

	public static boolean isConstructorEligibleHostile(Mob mob) {
		if (!(mob instanceof HostileMob) || mob instanceof FlyingHostileMob) return false;
		Package mobPackage = mob.getClass().getPackage();
		String packageName = mobPackage == null ? "" : mobPackage.getName();
		return !packageName.contains(".summon.") && !packageName.contains(".hostile.bosses.");
	}

	public static boolean isEligibleHostile(Mob mob) {
		if (!isConstructorEligibleHostile(mob)) return false;
		if (mob.isBoss() || mob.isSummoned || mob.isFlying()) return false;
		return mob.getLevelCollisionFilter() != null;
	}

	public static class HostileCrossLevelSmellAINode extends MoveTaskAINode {
		private static final int smellUpdateIntervalTicks = 40;
		private static final long serverTickMs = 50L;
		private static final long smellUpdateIntervalMs = smellUpdateIntervalTicks * serverTickMs;
		private static final long routeRetryCooldownMs = 1500L;

		private final TargetFinderAINode primaryTargetFinder;
		private final int normalSearchDistance;
		private Mob smellTarget;
		private SettlementLadderLink activeLink;
		private long nextSmellUpdateTime;
		private long nextRouteSearchTime;

		public HostileCrossLevelSmellAINode(TargetFinderAINode primaryTargetFinder) {
			this.primaryTargetFinder = primaryTargetFinder;
			this.normalSearchDistance = primaryTargetFinder.distance.searchDistance;
		}

		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
			// Stagger scans across the 40-tick window so a freshly loaded group of hostiles
			// does not all perform its cross-level smell search on the same server tick.
			long phaseTicks = Math.floorMod(mob.getUniqueID(), smellUpdateIntervalTicks);
			nextSmellUpdateTime = mob.getTime() + phaseTicks * serverTickMs;
		}

		@Override
		protected void onInterruptRunning(Mob mob, Blackboard blackboard) {
			super.onInterruptRunning(mob, blackboard);
			stop(mob, blackboard, true);
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {

			if (!isEligibleHostile(mob)) {
				stop(mob, blackboard, true);
				return AINodeResult.FAILURE;
			}

			Level level = mob.getLevel();
			if (level == null || !level.isServer() || level.getServer() == null) {
				stop(mob, blackboard, true);
				return AINodeResult.FAILURE;
			}

			boolean followingSmell = isFollowingSmell(blackboard);
			long now = mob.getTime();

			// Cross-level scent acquisition is deliberately sampled only once every 40
			// simulated server ticks (~2 seconds). Once a hostile is already following a
			// scent, keep ticking the active movement/route every tick so navigation and
			// ladder transitions remain smooth and immediately interruptible.
			if (!followingSmell) {
				if (now < nextSmellUpdateTime) return AINodeResult.FAILURE;
				nextSmellUpdateTime = now + smellUpdateIntervalMs;
			}

			if (!isIdleForSmell(mob, blackboard)) {
				stop(mob, blackboard, true);
				return AINodeResult.FAILURE;
			}

			SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomainQuiet(level.getServer(), level.getIdentifier(), mob.getTileX(), mob.getTileY());
			if (domain == null) {
				stop(mob, blackboard, true);
				return AINodeResult.FAILURE;
			}

			SettlementLevelType currentType = domain.getLevelType(level.getIdentifier());
			if (currentType == null) {
				stop(mob, blackboard, true);
				return AINodeResult.FAILURE;
			}
			SettlementLevelType targetType = currentType == SettlementLevelType.SURFACE ? SettlementLevelType.CAVE : SettlementLevelType.SURFACE;

			if (!isValidSmellTarget(mob, domain, targetType, smellTarget)) {
				boolean hadTarget = smellTarget != null;
				smellTarget = null;
				activeLink = null;
				clearTask();
				if (hadTarget) {
					nextSmellUpdateTime = now + smellUpdateIntervalMs;
					return AINodeResult.FAILURE;
				}
				smellTarget = findSmellTarget(mob, domain, targetType);
				if (smellTarget == null) return AINodeResult.FAILURE;
				if (Logging.logEnabled) Logging.logMessage("[HostileSmell] Hostile acquired cross-level scent mob=" + describeMob(mob)
						+ " target=" + describeMob(smellTarget) + " targetKind=" + describeSmellTargetKind(mob, smellTarget)
						+ " radiusTiles=" + getSmellRadiusTiles(domain));
				if (domain.getSettlement() != null) GuideProgressionSystem.onUndergroundThreat(domain.getSettlement());
			}

			if (activeLink == null) {
				if (now < nextRouteSearchTime) return AINodeResult.FAILURE;
				nextRouteSearchTime = now + routeRetryCooldownMs;
				activeLink = findBestPhysicalLink(mob, domain, currentType, smellTarget);
				if (activeLink == null) return AINodeResult.FAILURE;
				clearTask();
				if (blackboard.mover.isMoving() && !blackboard.mover.isCurrentlyMovingFor(this)) blackboard.mover.stopMoving(mob);
			}

			if (SettlementLadderSystem.isMobAtOrAdjacentLadder(mob, activeLink, currentType)) {
				if (blackboard.mover.isCurrentlyMovingFor(this)) blackboard.mover.stopMoving(mob);
				Mob target = smellTarget;
				SettlementLadderLink link = activeLink;
				boolean transitioned = SettlementLadderSystem.transitionMob(mob, link, targetType);
				stop(mob, blackboard, false);
				if (transitioned) {
					blackboard.submitEvent("resetPathTime", new AIEvent());
					blackboard.submitEvent("resetTarget", new AIEvent());
					if (Logging.logEnabled) Logging.logMessage("[HostileSmell] Hostile followed scent through physical ladder mob=" + describeMob(mob)
							+ " target=" + describeMob(target) + " via=" + link);
					return AINodeResult.RUNNING;
				}
				nextRouteSearchTime = now + routeRetryCooldownMs;
				return AINodeResult.FAILURE;
			}

			return super.tick(mob, blackboard);

		}

		@Override
		public AINodeResult tickNode(Mob mob, Blackboard blackboard) {

			if (activeLink == null || mob.getLevel() == null) return AINodeResult.FAILURE;
			SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomainQuiet(mob.getLevel().getServer(), mob.getLevel().getIdentifier(), mob.getTileX(), mob.getTileY());
			if (domain == null) return AINodeResult.FAILURE;
			SettlementLevelType currentType = domain.getLevelType(mob.getLevel().getIdentifier());
			if (currentType == null) return AINodeResult.FAILURE;

			if (blackboard.mover.isCurrentlyMovingFor(this)) return AINodeResult.RUNNING;
			int ladderX = activeLink.getTileX(currentType);
			int ladderY = activeLink.getTileY(currentType);
			return moveToTileTask(ladderX, ladderY, null, pathObject -> {
				MoveToTileAITask.AIPathResult path = (MoveToTileAITask.AIPathResult)pathObject;
				boolean moving = path.moveIfWithin(-1, 0, null);
				return moving ? AINodeResult.RUNNING : AINodeResult.FAILURE;
			});

		}

		@Override
		public AINodeResult onTaskFailed(Mob mob, Blackboard blackboard) {
			activeLink = null;
			nextRouteSearchTime = mob.getTime() + routeRetryCooldownMs;
			return AINodeResult.FAILURE;
		}

		private boolean isIdleForSmell(Mob mob, Blackboard blackboard) {
			if (blackboard.getLastHits().iterator().hasNext()) return false;

			if (blackboard.getObject(Mob.class, primaryTargetFinder.currentTargetKey) == null) {
				primaryTargetFinder.tickTargetFinder(mob, blackboard);
			}
			if (blackboard.getObject(Mob.class, primaryTargetFinder.currentTargetKey) != null) return false;
			if (blackboard.getObject(Mob.class, "chaserTarget") != null) return false;

			if (ZombieBreaching.isZombie(mob)) {
				if (blackboard.getObject(Mob.class, ZombieBreaching.passiveTargetKey) != null) return false;
				if (hasNearbyPassiveTarget(mob)) return false;
				ZombieBreaching.State breachState = ZombieBreaching.getState(mob);
				if (breachState.rememberedDoor != null || breachState.activeBreachTile != null || breachState.currentTarget != null) return false;
			}

			return true;
		}

		private boolean isFollowingSmell(Blackboard blackboard) {
			return smellTarget != null || activeLink != null || blackboard.mover.isCurrentlyMovingFor(this);
		}

		private boolean hasNearbyPassiveTarget(Mob zombie) {
			if (zombie.getLevel() == null || normalSearchDistance <= 0) return false;
			int tileRange = normalSearchDistance / 32 + 1;
			for (Mob candidate : zombie.getLevel().entityManager.mobs.getInRegionByTileRange(zombie.getTileX(), zombie.getTileY(), tileRange)) {
				if (!ZombiePassiveAggroPatch.isModdedPassiveAggroTarget(zombie, candidate)) continue;
				if (zombie.getDistance(candidate) < normalSearchDistance) return true;
			}
			return false;
		}

		private Mob findSmellTarget(Mob zombie, SettlementLevelDomain domain, SettlementLevelType targetType) {
			Level targetLevel = domain.getSettlement().getServer().world.levelManager.getLevel(domain.getLevelIdentifier(targetType));
			if (targetLevel == null) return null;
			int radiusTiles = getSmellRadiusTiles(domain);
			int radiusSquared = radiusTiles * radiusTiles;
			Mob best = null;
			int bestPriority = Integer.MAX_VALUE;
			int bestDistanceSquared = Integer.MAX_VALUE;

			for (Mob candidate : targetLevel.entityManager.mobs.getInRegionByTileRange(zombie.getTileX(), zombie.getTileY(), radiusTiles)) {
				int priority = getSmellAggroPriority(zombie, candidate);
				if (priority < 0) continue;
				if (!domain.isTileWithinBounds(targetLevel.getIdentifier(), candidate.getTileX(), candidate.getTileY())) continue;
				int dx = candidate.getTileX() - zombie.getTileX();
				int dy = candidate.getTileY() - zombie.getTileY();
				int distanceSquared = dx * dx + dy * dy;
				if (distanceSquared > radiusSquared) continue;
				if (priority > bestPriority || priority == bestPriority && distanceSquared >= bestDistanceSquared) continue;
				best = candidate;
				bestPriority = priority;
				bestDistanceSquared = distanceSquared;
			}
			return best;
		}

		private boolean isValidSmellTarget(Mob zombie, SettlementLevelDomain domain, SettlementLevelType targetType, Mob target) {
			if (!isSmellAggroTarget(zombie, target)) return false;
			if (target.getLevel() == null || domain.getLevelType(target.getLevel().getIdentifier()) != targetType) return false;
			if (!domain.isTileWithinBounds(target.getLevel().getIdentifier(), target.getTileX(), target.getTileY())) return false;
			int radiusTiles = getSmellRadiusTiles(domain);
			int dx = target.getTileX() - zombie.getTileX();
			int dy = target.getTileY() - zombie.getTileY();
			return dx * dx + dy * dy <= radiusTiles * radiusTiles;
		}

		private boolean isSmellAggroTarget(Mob hostile, Mob target) {
			return getSmellAggroPriority(hostile, target) >= 0;
		}

		private int getSmellAggroPriority(Mob hostile, Mob target) {
			if (target == null || target == hostile || target.removed() || target.getHealth() <= 0 || !target.isVisible()) return -1;

			if (isPrimaryAggroTargetType(target)) {
				return !target.isSameTeam(hostile)
						&& !hostile.isInAttackOwnerChain(target)
						&& hostile.canTarget(target) ? 0 : -1;
			}

			return ZombiePassiveAggroPatch.isModdedPassiveAggroTarget(hostile, target) ? 1 : -1;
		}

		private boolean isPrimaryAggroTargetType(Mob target) {
			int team = target.getTeam();
			return team == -100 || target.isHuman && team != -1 || target.isPlayer;
		}

		private SettlementLadderLink findBestPhysicalLink(Mob zombie, SettlementLevelDomain domain, SettlementLevelType currentType, Mob target) {
			List<SettlementLadderLink> links = SettlementLadderSystem.getPhysicalLinks(domain);
			SettlementLadderLink best = null;
			double bestScore = Double.MAX_VALUE;
			SettlementLevelType destinationType = currentType == SettlementLevelType.SURFACE ? SettlementLevelType.CAVE : SettlementLevelType.SURFACE;

			for (SettlementLadderLink link : links) {
				int sourceX = link.getTileX(currentType);
				int sourceY = link.getTileY(currentType);
				if (!zombie.estimateCanMoveTo(sourceX, sourceY, false)) continue;
				int destinationX = link.getTileX(destinationType);
				int destinationY = link.getTileY(destinationType);
				double sourceDistance = Math.hypot(sourceX - zombie.getTileX(), sourceY - zombie.getTileY());
				double targetDistance = Math.hypot(destinationX - target.getTileX(), destinationY - target.getTileY());
				double score = sourceDistance + targetDistance;
				if (score < bestScore) {
					best = link;
					bestScore = score;
				}
			}
			return best;
		}

		private int getSmellRadiusTiles(SettlementLevelDomain domain) {
			Rectangle bounds = domain.getTileBounds();
			if (bounds == null) return 1;
			int settlementRadius = Math.max(1, Math.min(bounds.width, bounds.height) / 2);
			return Math.max(1, settlementRadius / 2);
		}

		private void stop(Mob mob, Blackboard blackboard, boolean clearTarget) {
			clearTask();
			if (blackboard.mover.isCurrentlyMovingFor(this)) blackboard.mover.stopMoving(mob);
			activeLink = null;
			if (clearTarget) smellTarget = null;
		}

		private String describeSmellTargetKind(Mob hostile, Mob target) {
			if (target == null) return "none";
			if (target.isPlayer) return "player";
			if (target.isHuman) return "human";
			if (ZombiePassiveAggroPatch.isModdedPassiveAggroTarget(hostile, target)) return "passiveMob";
			return "other";
		}

		private String describeMob(Mob mob) {
			if (mob == null) return "null";
			return mob.getStringID() + "#" + mob.getUniqueID() + "@" + (mob.getLevel() == null ? "null" : mob.getLevel().getIdentifier())
					+ ":" + mob.getTileX() + "," + mob.getTileY();
		}
	}
}
