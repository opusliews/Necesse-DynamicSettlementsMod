package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanJobSearchingAINode;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardDutySystem;
import opus.guard.GuardNeedsSystem;

@ModMethodPatch(
		target = HumanJobSearchingAINode.class,
		name = "tick",
		arguments = {HumanMob.class, Blackboard.class}
)
public class GuardDutyJobSearchPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This HumanJobSearchingAINode node,
			@Advice.Argument(0) HumanMob mob
	) {
		if (!(mob instanceof GuardHumanMob)) {
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!GuardDutySystem.usesNightSchedule(guard)) {
			return;
		}

		if (!guard.getWorldEntity().isNight()) {
			GuardNeedsSystem.releaseBreak(guard, false);
			guard.cancelJob();
		}
		else if (GuardNeedsSystem.shouldYieldPatrolForBreak(guard)) {
			node.nextSearchTime = 0L;
		}
	}
}
