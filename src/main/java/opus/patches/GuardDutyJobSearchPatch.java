package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanJobSearchingAINode;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardDutySystem;

@ModMethodPatch(
		target = HumanJobSearchingAINode.class,
		name = "tick",
		arguments = {HumanMob.class, Blackboard.class}
)
public class GuardDutyJobSearchPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static AINodeResult onEnter(@Advice.Argument(0) HumanMob mob) {
		if (mob instanceof GuardHumanMob && GuardDutySystem.usesNightSchedule((GuardHumanMob)mob)) {
			return AINodeResult.FAILURE;
		}

		return null;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter AINodeResult dutyResult,
			@Advice.Return(readOnly = false) AINodeResult result
	) {
		if (dutyResult != null) {
			result = dutyResult;
		}
	}
}
