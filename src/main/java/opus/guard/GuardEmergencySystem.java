package opus.guard;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.CompositeTypedAINode;
import necesse.entity.mobs.ai.behaviourTree.event.AIEvent;
import necesse.entity.mobs.friendly.human.GuardHumanMob;

public final class GuardEmergencySystem {
	private GuardEmergencySystem() {
	}

	public static void preempt(GuardHumanMob guard) {
		preemptForTarget(guard, null);
	}

	public static void preemptForTarget(GuardHumanMob guard, Mob target) {
		if (guard == null) {
			return;
		}

		GuardDutySystem.releasePatrolTarget(guard);
		GuardNeedsSystem.interruptBreak(guard);
		guard.cancelJob();
		if (guard.objectUser != null) {
			guard.objectUser.stopUsing();
		}

		if (guard.ai != null) {
			if (guard.ai.tree instanceof CompositeTypedAINode) {
				((CompositeTypedAINode)guard.ai.tree).onInterruptRunning(guard, guard.ai.blackboard);
			}
			if (target != null && !target.removed() && target.getHealth() > 0 && target.isSamePlace(guard)) {
				guard.ai.blackboard.put("currentTarget", target);
			}
			guard.ai.blackboard.submitEvent("resetPathTime", new AIEvent());
		}
	}
}
