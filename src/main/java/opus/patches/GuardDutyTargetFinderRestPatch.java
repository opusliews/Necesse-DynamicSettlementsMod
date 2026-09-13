package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.ComputedValue;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.trees.HumanTargetFinderAI;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardFatigueSystem;

@ModMethodPatch(
		target = HumanTargetFinderAI.class,
		name = "isValidTarget",
		arguments = {HumanMob.class, Mob.class, boolean.class, boolean.class, ComputedValue.class, boolean.class}
)
public class GuardDutyTargetFinderRestPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This HumanTargetFinderAI finder,
			@Advice.Argument(0) HumanMob mob,
			@Advice.Argument(1) Mob target
	) {
		if (!(mob instanceof GuardHumanMob)) {
			return false;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		return GuardFatigueSystem.isScheduledRestPeriod(guard)
				&& !finder.humanAngerTargetAINode.enemies.contains(target);
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Enter boolean blocked, @Advice.Return(readOnly = false) boolean result) {
		if (blocked) {
			result = false;
		}
	}
}
