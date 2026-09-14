package opusliews.sleep;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.FollowerAINode;
import necesse.entity.mobs.friendly.human.GuardHumanMob;

public class GuardWakePlayerAINode extends FollowerAINode {
	private static final float wakeDistance = 96.0F;
	private static final float stuckWakeDistance = 256.0F;
	private static final float progressDistance = 8.0F;
	private static final long stuckTimeoutNanos = 1_000_000_000L;
	private static final long postWakeHoldNanos = 400_000_000L;

	private long holdUntilNanos;
	private int trackedTargetUniqueID = Integer.MIN_VALUE;
	private float bestDistance = Float.MAX_VALUE;
	private long lastProgressNanos;

	public GuardWakePlayerAINode() {
		super(0, 64);
	}

	@Override
	public AINodeResult tickNode(Mob mob, Blackboard blackboard) {
		if (!(mob instanceof GuardHumanMob)) {
			return AINodeResult.FAILURE;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		long now = System.nanoTime();

		if (holdUntilNanos > 0L) {
			if (now < holdUntilNanos) {
				if (blackboard.mover.isCurrentlyMovingFor(this)) {
					blackboard.mover.stopMoving(guard);
				}
				return AINodeResult.RUNNING;
			}

			holdUntilNanos = 0L;
			resetProgress();
			return AINodeResult.SUCCESS;
		}

		PlayerMob target = SleepWarningSystem.getWakeTarget(guard);
		if (target == null) {
			resetProgress();
			return super.tickNode(mob, blackboard);
		}

		float distance = guard.getDistance(target);
		if (trackedTargetUniqueID != target.getUniqueID()) {
			trackedTargetUniqueID = target.getUniqueID();
			bestDistance = distance;
			lastProgressNanos = now;
		}
		else if (distance < bestDistance - progressDistance) {
			bestDistance = distance;
			lastProgressNanos = now;
		}

		AINodeResult result = super.tickNode(mob, blackboard);

		distance = guard.getDistance(target);
		if (distance < bestDistance - progressDistance) {
			bestDistance = distance;
			lastProgressNanos = System.nanoTime();
		}

		boolean moving = blackboard.mover.isCurrentlyMovingFor(this);
		boolean reachedBestPoint = !moving && distance <= wakeDistance;
		boolean stuck = !moving
				&& distance <= stuckWakeDistance
				&& System.nanoTime() - lastProgressNanos >= stuckTimeoutNanos;

		if (reachedBestPoint || stuck) {
			if (blackboard.mover.isCurrentlyMovingFor(this)) {
				blackboard.mover.stopMoving(guard);
			}

			SleepWarningSystem.completeWake(guard);
			holdUntilNanos = System.nanoTime() + postWakeHoldNanos;
			return AINodeResult.RUNNING;
		}

		return result;
	}

	@Override
	public Mob getFollowingMob(Mob mob) {
		return mob instanceof GuardHumanMob ? SleepWarningSystem.getWakeTarget((GuardHumanMob)mob) : null;
	}

	private void resetProgress() {
		trackedTargetUniqueID = Integer.MIN_VALUE;
		bestDistance = Float.MAX_VALUE;
		lastProgressNanos = 0L;
	}
}
