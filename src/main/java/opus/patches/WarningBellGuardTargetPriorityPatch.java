package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.ComputedValue;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.trees.HumanTargetFinderAI;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.breaching.WarningBellSystem;
import opus.logging.Logging;

@ModMethodPatch(
		target = HumanTargetFinderAI.class,
		name = "isValidTarget",
		arguments = {HumanMob.class, Mob.class, boolean.class, boolean.class, ComputedValue.class, boolean.class}
)
public class WarningBellGuardTargetPriorityPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) HumanMob mob,
			@Advice.Argument(1) Mob target,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (!(mob instanceof GuardHumanMob)) {
			return;
		}

		Mob warningTarget = WarningBellSystem.getGuardTargetMob((GuardHumanMob)mob);
		if (warningTarget == null) {
			return;
		}

		boolean forcedValid = target == warningTarget;
		if (result != forcedValid) {
			Logging.logMessage("WarningBell: guard " + mob.getUniqueID()
					+ " target priority override: candidate="
					+ (target == null ? "null" : target.getStringID() + "#" + target.getUniqueID())
					+ " forced=" + warningTarget.getStringID() + "#" + warningTarget.getUniqueID()
					+ " valid=" + forcedValid);
		}
		result = forcedValid;
	}
}
