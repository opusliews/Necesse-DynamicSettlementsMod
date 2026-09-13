package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanSleepAINode;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardDutySystem;

@ModMethodPatch(target = HumanSleepAINode.class, name = "shouldSleep", arguments = {HumanMob.class})
public class GuardDutySleepPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) HumanMob mob,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (!(mob instanceof GuardHumanMob)) {
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (GuardDutySystem.usesNightSchedule(guard)) {
			result = guard.isHiding || !guard.getWorldEntity().isNight();
		}
	}
}
