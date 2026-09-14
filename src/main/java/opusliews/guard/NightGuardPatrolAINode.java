package opusliews.guard;

import java.awt.Point;
import java.awt.Rectangle;
import necesse.engine.util.GameRandom;
import necesse.engine.util.pathfinding.Pathfinding;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.decorators.MoveTaskAINode;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.ZoneTester;

public class NightGuardPatrolAINode extends MoveTaskAINode {
	private static final int candidateAttempts = 60;
	private static final int localCandidateRadius = 30;
	private static final int minimumTravelDistance = 7;
	private static final int targetReachedDistance = 2;
	private static final int minPauseMs = 1200;
	private static final int maxPauseMs = 3500;

	private Point patrolTarget;
	private long nextTargetTime;

	@Override
	protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
	}

	@Override
	public void init(Mob mob, Blackboard blackboard) {
	}

	@Override
	protected void onInterruptRunning(Mob mob, Blackboard blackboard) {
		super.onInterruptRunning(mob, blackboard);
		GuardHumanMob guard = (GuardHumanMob)mob;
		clearTarget(guard);
		if (blackboard.mover.isCurrentlyMovingFor(this)) {
			blackboard.mover.stopMoving(guard);
		}
	}

	@Override
	public AINodeResult tickNode(Mob mob, Blackboard blackboard) {
		GuardHumanMob guard = (GuardHumanMob)mob;
		if (guard.isBeingInteractedWith()) {
			clearTarget(guard);
			if (blackboard.mover.isCurrentlyMovingFor(this)) {
				blackboard.mover.stopMoving(guard);
			}
			return AINodeResult.FAILURE;
		}

		if (GuardNeedsSystem.shouldYieldPatrolForBreak(guard)) {
			clearTarget(guard);
			if (blackboard.mover.isCurrentlyMovingFor(this)) {
				blackboard.mover.stopMoving(guard);
			}
			return AINodeResult.FAILURE;
		}

		if (!GuardDutySystem.shouldPatrol(guard)) {
			clearTarget(guard);
			if (blackboard.mover.isCurrentlyMovingFor(this)) {
				blackboard.mover.stopMoving(guard);
			}
			return AINodeResult.FAILURE;
		}

		if (guard.objectUser != null) {
			guard.objectUser.stopUsing();
		}

		Level level = guard.getLevel();
		long now = level.getTime();

		if (patrolTarget != null && !isTargetStillValid(guard, patrolTarget)) {
			clearTarget(guard);
		}

		if (patrolTarget != null && isNearTarget(guard, patrolTarget)) {
			clearTarget(guard);
			nextTargetTime = now + GameRandom.globalRandom.getIntBetween(minPauseMs, maxPauseMs);
			return AINodeResult.SUCCESS;
		}

		if (blackboard.mover.isCurrentlyMovingFor(this)) {
			return AINodeResult.RUNNING;
		}

		if (patrolTarget == null) {
			if (now < nextTargetTime) {
				return AINodeResult.SUCCESS;
			}

			patrolTarget = findPatrolTarget(guard);
			if (patrolTarget == null) {
				nextTargetTime = now + 1000L;
				return AINodeResult.SUCCESS;
			}

			GuardDutySystem.claimPatrolTarget(guard, patrolTarget);
		}

		Point target = patrolTarget;
		return moveToTileTask(target.x, target.y, null, pathObject -> {
			MoveToTileAITask.AIPathResult path = (MoveToTileAITask.AIPathResult)pathObject;
			if (!path.result.foundTarget || !isOutdoorPatrolPath(level, path.result.path)) {
				clearTarget(guard);
				nextTargetTime = level.getTime() + 500L;
				return AINodeResult.SUCCESS;
			}

			if (path.moveIfWithin(-1, -1, null)) {
				return AINodeResult.RUNNING;
			}

			clearTarget(guard);
			nextTargetTime = level.getTime() + 500L;
			return AINodeResult.SUCCESS;
		});
	}

	private Point findPatrolTarget(GuardHumanMob guard) {
		ServerSettlementData settlement = guard.getSettlerSettlementServerData();
		if (settlement == null || guard.levelSettler == null) {
			return null;
		}

		Rectangle bounds = settlement.boundsManager.getTileRectangle();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
			return null;
		}

		ZoneTester zoneTester = guard.levelSettler.isTileInSettlementBoundsAndRestrictZoneTester();
		Level level = guard.getLevel();
		Point best = null;
		int bestSpacing = -1;
		int bestTravel = -1;

		for (int i = 0; i < candidateAttempts; i++) {
			int x;
			int y;
			if (i < candidateAttempts / 2) {
				x = guard.getTileX() + GameRandom.globalRandom.getIntBetween(-localCandidateRadius, localCandidateRadius);
				y = guard.getTileY() + GameRandom.globalRandom.getIntBetween(-localCandidateRadius, localCandidateRadius);
			}
			else {
				x = bounds.x + GameRandom.globalRandom.nextInt(bounds.width);
				y = bounds.y + GameRandom.globalRandom.nextInt(bounds.height);
			}

			if (!bounds.contains(x, y)
					|| !zoneTester.containsTile(x, y)
					|| !level.isOutside(x, y)
					|| level.isSolidTile(x, y)
					|| level.isLiquidTile(x, y)) {
				continue;
			}

			int dx = x - guard.getTileX();
			int dy = y - guard.getTileY();
			int travelDistance = dx * dx + dy * dy;
			if (travelDistance < minimumTravelDistance * minimumTravelDistance) {
				continue;
			}

			if (!guard.estimateCanMoveTo(x, y, false)) {
				continue;
			}

			Point candidate = new Point(x, y);
			int spacing = GuardDutySystem.getPatrolSpacingScore(guard, candidate);
			if (spacing > bestSpacing || spacing == bestSpacing && travelDistance > bestTravel) {
				best = candidate;
				bestSpacing = spacing;
				bestTravel = travelDistance;
			}
		}

		return best;
	}

	private boolean isTargetStillValid(GuardHumanMob guard, Point target) {
		if (guard.levelSettler == null) {
			return false;
		}

		Level level = guard.getLevel();
		ZoneTester zoneTester = guard.levelSettler.isTileInSettlementBoundsAndRestrictZoneTester();
		return zoneTester.containsTile(target.x, target.y)
				&& level.isOutside(target.x, target.y)
				&& !level.isSolidTile(target.x, target.y)
				&& !level.isLiquidTile(target.x, target.y);
	}

	private boolean isNearTarget(GuardHumanMob guard, Point target) {
		int dx = target.x - guard.getTileX();
		int dy = target.y - guard.getTileY();
		return dx * dx + dy * dy <= targetReachedDistance * targetReachedDistance;
	}

	private boolean isOutdoorPatrolPath(Level level, Iterable pathNodes) {
		boolean reachedOutside = false;

		for (Object nodeObject : pathNodes) {
			Pathfinding.Node node = (Pathfinding.Node)nodeObject;
			if (!(node.item instanceof Point)) {
				continue;
			}

			Point tile = (Point)node.item;
			if (level.isOutside(tile.x, tile.y)) {
				reachedOutside = true;
			}
			else if (reachedOutside) {
				return false;
			}
		}

		return reachedOutside;
	}

	private void clearTarget(GuardHumanMob guard) {
		patrolTarget = null;
		GuardDutySystem.releasePatrolTarget(guard);
	}
}
