package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.ComputedValue;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.trees.HumanTargetFinderAI;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardFatigueSystem;
import opus.sleep.SleepWarningSystem;

@ModMethodPatch(
		target = HumanTargetFinderAI.class,
		name = "isValidTarget",
		arguments = {HumanMob.class, Mob.class, boolean.class, boolean.class, ComputedValue.class, boolean.class}
)
public class GuardDutyTargetFinderRestPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(0) HumanMob mob) {
		if (!(mob instanceof GuardHumanMob)) {
			return false;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (SleepWarningSystem.hasWakeAssignment(guard)) {
			return true;
		}

		if (SleepWarningSystem.isRaidActiveForGuard(guard)) {
			return false;
		}

		return GuardFatigueSystem.isScheduledRestPeriod(guard);
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Enter boolean blocked, @Advice.Return(readOnly = false) boolean result) {
		if (blocked) {
			result = false;
		}
	}
}
