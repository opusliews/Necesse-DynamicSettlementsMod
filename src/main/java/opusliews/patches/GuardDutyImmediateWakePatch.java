package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanSleepAINode;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardDutySystem;

@ModMethodPatch(
		target = HumanSleepAINode.class,
		name = "tick",
		arguments = {HumanMob.class, Blackboard.class}
)
public class GuardDutyImmediateWakePatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.Argument(0) HumanMob mob) {
		if (!(mob instanceof GuardHumanMob)) {
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;

		if (!GuardDutySystem.usesNightSchedule(guard)
				|| !guard.getWorldEntity().isNight()
				|| guard.isHiding) {
			return;
		}

		if (guard.objectUser != null) {
			guard.objectUser.stopUsing();
		}
	}
}