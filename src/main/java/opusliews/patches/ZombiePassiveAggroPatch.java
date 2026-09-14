package opusliews.patches;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Locale;

import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.engine.util.GameMath;
import necesse.engine.util.gameAreaSearch.GameAreaStream;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.BehaviourTreeAI;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.composites.SelectorAINode;
import necesse.entity.mobs.ai.behaviourTree.decorators.MoveTaskAINode;
import necesse.entity.mobs.ai.behaviourTree.event.AIEvent;
import necesse.entity.mobs.ai.behaviourTree.event.AIWasHitEvent;
import necesse.entity.mobs.ai.behaviourTree.leaves.TargetFinderAINode;
import necesse.entity.mobs.ai.behaviourTree.leaves.WandererAINode;
import necesse.entity.mobs.ai.behaviourTree.trees.ChaserAI;
import necesse.entity.mobs.ai.behaviourTree.trees.CollisionChaserAI;
import necesse.entity.mobs.ai.behaviourTree.trees.ConfusedCollisionPlayerChaserWandererAI;
import necesse.entity.mobs.ai.behaviourTree.trees.ConfusedPlayerChaserWandererAI;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import necesse.entity.mobs.ai.behaviourTree.util.TargetFinderDistance;
import necesse.entity.mobs.friendly.FriendlyMob;
import necesse.entity.mobs.friendly.PolarBearMob;
import necesse.level.maps.levelData.settlementData.ZoneTester;
import net.bytebuddy.asm.Advice;

@ModConstructorPatch(
		target = BehaviourTreeAI.class,
		arguments = {Mob.class, AINode.class, AIMover.class}
)
public class ZombiePassiveAggroPatch {
	private static final String passiveTargetKey = "dynamicSettlementsZombiePassiveTarget";
	private static final String passiveFocusTargetKey = "dynamicSettlementsZombiePassiveFocusTarget";
	private static final String passiveChaserTargetKey = "dynamicSettlementsZombiePassiveChaserTarget";
	private static final String panicEventType = "dynamicSettlementsZombiePanic";

	// Passive mobs inside this radius are warned when one of them takes real zombie damage.
	private static final int panicAlertRangeTiles = 10;

	// Animals keep fleeing until the zombie is at least this far away.
	private static final int panicCalmDistanceTiles = 9;

	// Each flee path tries to move roughly this many tiles farther from the zombie.
	private static final int panicRunAheadTiles = 4;
	private static final int panicSearchRadiusTiles = 6;
	private static final long minimumPanicTime = 3000L;
	private static final long panicRepathCooldown = 500L;

	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.Argument(0) Mob mob,
			@Advice.Argument(value = 1, readOnly = false) AINode tree
	) {
		tree = wrapWithZombiePanicAI(mob, tree);
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) Mob mob, @Advice.Argument(1) AINode tree) {
		installZombieAggro(mob, tree);
	}

	public static AINode wrapWithZombiePanicAI(Mob mob, AINode tree) {
		return canHaveZombiePanicAI(mob) ? new ZombiePanicRoot(tree) : tree;
	}

	public static void installZombieAggro(Mob mob, AINode tree) {
		if (!isZombieEnemy(mob)) {
			return;
		}

		if (tree instanceof ConfusedCollisionPlayerChaserWandererAI) {
			addMeleePassiveTargeting((ConfusedCollisionPlayerChaserWandererAI)tree);
		}
		else if (tree instanceof ConfusedPlayerChaserWandererAI) {
			addRangedPassiveTargeting((ConfusedPlayerChaserWandererAI)tree);
		}
	}

	private static boolean isZombieEnemy(Mob mob) {
		if (mob == null || !mob.isHostile) {
			return false;
		}

		String stringID = mob.getStringID();
		return stringID != null && stringID.toLowerCase(Locale.ROOT).contains("zombie");
	}

	private static boolean isPassiveMob(Mob target) {
		return target instanceof FriendlyMob
				&& !target.isHostile
				&& !target.isPlayer
				&& !target.isHuman
				&& !target.removed()
				&& target.isVisible();
	}

	private static boolean canHaveZombiePanicAI(Mob mob) {
		return mob instanceof FriendlyMob
				&& !(mob instanceof PolarBearMob)
				&& !mob.isPlayer
				&& !mob.isHuman;
	}

	private static boolean canStartZombiePanic(Mob mob) {
		return canHaveZombiePanicAI(mob) && !mob.isHostile && !mob.removed();
	}

	private static GameAreaStream streamPassiveMobs(Mob mob, Point base, TargetFinderDistance distance) {
		return distance.streamMobsInRange(base, mob).filter(target -> isPassiveMob((Mob)target));
	}

	private static void addMeleePassiveTargeting(ConfusedCollisionPlayerChaserWandererAI root) {
		final TargetFinderAINode playerTargetFinder = root.collisionPlayerChaserAI.targetFinderAINode;
		final CollisionChaserAI passiveChaser = new CollisionChaserAI(
				playerTargetFinder.distance.searchDistance,
				root.collisionPlayerChaserAI.damage,
				root.collisionPlayerChaserAI.knockback
		) {
			@Override
			public GameAreaStream streamPossibleTargets(Mob mob, Point base, TargetFinderDistance distance) {
				return streamPassiveMobs(mob, base, distance);
			}

			@Override
			public boolean attackTarget(Mob mob, Mob target) {
				return root.attackTarget(mob, target);
			}
		};

		configurePassiveCollisionChaser(passiveChaser);
		root.addChildBefore(root.wandererAINode, new PlayerPriorityRefreshAINode(playerTargetFinder));
		root.addChildBefore(root.wandererAINode, passiveChaser);
	}

	private static void addRangedPassiveTargeting(ConfusedPlayerChaserWandererAI root) {
		final TargetFinderAINode playerTargetFinder = root.playerChaserAI.targetFinderAINode;
		final ChaserAI passiveChaser = new ChaserAI(
				playerTargetFinder.distance.searchDistance,
				root.playerChaserAI.chaserAINode.attackDistance,
				root.playerChaserAI.chaserAINode.smartPositioning,
				root.playerChaserAI.chaserAINode.changePositionOnHit
		) {
			@Override
			public GameAreaStream streamPossibleTargets(Mob mob, Point base, TargetFinderDistance distance) {
				return streamPassiveMobs(mob, base, distance);
			}

			@Override
			public boolean canHitTarget(Mob mob, float fromX, float fromY, Mob target) {
				return root.canHitTarget(mob, fromX, fromY, target);
			}

			@Override
			public boolean attackTarget(Mob mob, Mob target) {
				return root.attackTarget(mob, target);
			}
		};

		configurePassiveChaser(passiveChaser);
		root.addChildBefore(root.wandererAINode, new PlayerPriorityRefreshAINode(playerTargetFinder));
		root.addChildBefore(root.wandererAINode, passiveChaser);
	}

	private static void configurePassiveCollisionChaser(CollisionChaserAI passiveChaser) {
		passiveChaser.looseTargetTimerAINode.currentTargetKey = passiveTargetKey;
		passiveChaser.targetFinderAINode.currentTargetKey = passiveTargetKey;
		passiveChaser.targetFinderAINode.focusTargetKey = passiveFocusTargetKey;
		passiveChaser.targetFinderAINode.newTargetFoundEventType = "dynamicSettlementsZombiePassiveTargetFound";
		passiveChaser.targetFinderAINode.lastTargetInvalidEventType = "dynamicSettlementsZombiePassiveTargetInvalid";
		passiveChaser.targetFinderAINode.moveToAttacker = false;
		passiveChaser.collisionChaserAINode.targetKey = passiveTargetKey;
		passiveChaser.collisionChaserAINode.chaserTargetKey = passiveChaserTargetKey;
	}

	private static void configurePassiveChaser(ChaserAI passiveChaser) {
		passiveChaser.targetFinderAINode.currentTargetKey = passiveTargetKey;
		passiveChaser.targetFinderAINode.focusTargetKey = passiveFocusTargetKey;
		passiveChaser.targetFinderAINode.newTargetFoundEventType = "dynamicSettlementsZombiePassiveTargetFound";
		passiveChaser.targetFinderAINode.lastTargetInvalidEventType = "dynamicSettlementsZombiePassiveTargetInvalid";
		passiveChaser.targetFinderAINode.moveToAttacker = false;
		passiveChaser.chaserAINode.targetKey = passiveTargetKey;
		passiveChaser.chaserAINode.chaserTargetKey = passiveChaserTargetKey;
	}

	private static void alertNearbyPassiveMobs(Mob victim, Mob zombie) {
		victim.getLevel().entityManager.mobs
				.getInRegionByTileRange(victim.getTileX(), victim.getTileY(), panicAlertRangeTiles)
				.forEach(other -> {
					if (other != victim && canStartZombiePanic(other) && other.ai != null) {
						other.ai.blackboard.submitEvent(panicEventType, new ZombiePanicEvent(zombie));
					}
				});
	}

	private static class PlayerPriorityRefreshAINode extends AINode {
		private final TargetFinderAINode playerTargetFinder;

		private PlayerPriorityRefreshAINode(TargetFinderAINode playerTargetFinder) {
			this.playerTargetFinder = playerTargetFinder;
		}

		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			if (blackboard.getObject(Mob.class, passiveTargetKey) != null) {
				playerTargetFinder.noTargetFoundTimer = -1;
			}

			return AINodeResult.FAILURE;
		}
	}

	private static class ZombiePanicRoot extends SelectorAINode {
		private ZombiePanicRoot(AINode originalTree) {
			this.addChild(new ZombiePanicAINode());
			this.addChild(originalTree);
		}
	}

	private static class ZombiePanicAINode extends MoveTaskAINode {
		private Mob threat;
		private long minimumPanicUntil;
		private long nextPathAttemptTime;

		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
			blackboard.onWasHit(event -> {
				if (!(event instanceof AIWasHitEvent)) {
					return;
				}

				AIWasHitEvent hit = (AIWasHitEvent)event;
				Mob attackOwner = hit.event.attacker != null ? hit.event.attacker.getAttackOwner() : null;
				boolean tookHealthDamage = !hit.event.wasPrevented && hit.event.beforeHealth > mob.getHealth();
				if (tookHealthDamage && isZombieEnemy(attackOwner) && canStartZombiePanic(mob)) {
					this.startPanic(mob, blackboard, attackOwner);
					alertNearbyPassiveMobs(mob, attackOwner);
				}
			});

			blackboard.onEvent(panicEventType, event -> {
				if (event instanceof ZombiePanicEvent && canStartZombiePanic(mob)) {
					Mob zombie = ((ZombiePanicEvent)event).zombie;
					if (isZombieEnemy(zombie)) {
						this.startPanic(mob, blackboard, zombie);
					}
				}
			});
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			if (!this.isPanicActive(mob)) {
				this.stopPanic(mob, blackboard);
				return AINodeResult.FAILURE;
			}

			return super.tick(mob, blackboard);
		}

		@Override
		public AINodeResult tickNode(Mob mob, Blackboard blackboard) {
			if (blackboard.mover.isCurrentlyMovingFor(this)) {
				return AINodeResult.RUNNING;
			}

			if (mob.getTime() < this.nextPathAttemptTime) {
				return AINodeResult.RUNNING;
			}

			this.nextPathAttemptTime = mob.getTime() + panicRepathCooldown;
			Point runPoint = this.findRunPoint(mob, this.threat);
			if (runPoint == null) {
				return AINodeResult.RUNNING;
			}

			return this.moveToTileTask(runPoint.x, runPoint.y, null, pathObject -> {
				MoveToTileAITask.AIPathResult path = (MoveToTileAITask.AIPathResult)pathObject;
				path.moveIfWithin(-1, -1, null);
				return AINodeResult.RUNNING;
			});
		}

		@Override
		public AINodeResult onTaskFailed(Mob mob, Blackboard blackboard) {
			this.nextPathAttemptTime = mob.getTime() + panicRepathCooldown;
			return this.isPanicActive(mob) ? AINodeResult.RUNNING : AINodeResult.FAILURE;
		}

		private void startPanic(Mob mob, Blackboard blackboard, Mob zombie) {
			this.threat = zombie;
			this.minimumPanicUntil = Math.max(this.minimumPanicUntil, mob.getTime() + minimumPanicTime);
			this.nextPathAttemptTime = 0L;
			this.clearTask();
			if (blackboard.mover.isMoving()) {
				blackboard.mover.stopMoving(mob);
			}
			this.forceSetRunning();
		}

		private void stopPanic(Mob mob, Blackboard blackboard) {
			this.clearTask();
			if (blackboard.mover.isCurrentlyMovingFor(this)) {
				blackboard.mover.stopMoving(mob);
			}
			this.threat = null;
			this.minimumPanicUntil = 0L;
			this.nextPathAttemptTime = 0L;
		}

		private boolean isPanicActive(Mob mob) {
			if (this.threat == null || this.threat.removed() || !this.threat.isSamePlace(mob)) {
				return false;
			}

			if (mob.getTime() < this.minimumPanicUntil) {
				return true;
			}

			// Some neutral FriendlyMob classes (for example crocodiles/thrumbos)
			// temporarily mark themselves hostile after being hit. Keep panic in control
			// until their vanilla retaliation state clears, so polar bears remain the
			// only passive zombie prey that actually fights back.
			if (mob.isHostile) {
				return true;
			}

			return mob.getDistance(this.threat) < panicCalmDistanceTiles * 32.0F;
		}

		private Point findRunPoint(Mob mob, Mob zombie) {
			float dx = mob.x - zombie.x;
			float dy = mob.y - zombie.y;
			Point2D.Float dir = GameMath.normalize(dx, dy);
			if (dir.x == 0.0F && dir.y == 0.0F) {
				dir.x = 1.0F;
			}

			float dirMod = Math.abs(dir.x) > Math.abs(dir.y) ? 1.0F / Math.abs(dir.x) : 1.0F / Math.abs(dir.y);
			dir.x *= dirMod;
			dir.y *= dirMod;

			int currentTileX = mob.getTileX();
			int currentTileY = mob.getTileY();
			int zombieTileX = zombie.getTileX();
			int zombieTileY = zombie.getTileY();
			double currentDistance = GameMath.diagonalMoveDistance(currentTileX, currentTileY, zombieTileX, zombieTileY);
			double requiredDistance = Math.min(panicCalmDistanceTiles, currentDistance + 4.0);
			ZoneTester awayFromZombie = (tileX, tileY) ->
					GameMath.diagonalMoveDistance(tileX, tileY, zombieTileX, zombieTileY) >= requiredDistance;

			int targetTileX = currentTileX + Math.round(dir.x * panicRunAheadTiles);
			int targetTileY = currentTileY + Math.round(dir.y * panicRunAheadTiles);
			Point runPoint = WandererAINode.findWanderingPointAround(
					mob,
					targetTileX,
					targetTileY,
					panicSearchRadiusTiles,
					awayFromZombie,
					20,
					5
			);

			if (runPoint == null) {
				runPoint = WandererAINode.findWanderingPointAround(
						mob,
						currentTileX,
						currentTileY,
						panicSearchRadiusTiles + 2,
						awayFromZombie,
						20,
						5
				);
			}

			return runPoint;
		}
	}

	private static class ZombiePanicEvent extends AIEvent {
		private final Mob zombie;

		private ZombiePanicEvent(Mob zombie) {
			this.zombie = zombie;
		}
	}
}
