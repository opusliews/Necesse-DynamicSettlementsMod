package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardDutySystem;

@ModMethodPatch(target = HumanMob.class, name = "wantsToDoRecreation", arguments = {})
public class GuardDutyWantsRecreationPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob mob, @Advice.Return(readOnly = false) boolean result) {
		if (!(mob instanceof GuardHumanMob)) {
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (GuardDutySystem.usesNightSchedule(guard)) {
			result = guard.getWorldEntity().isNight() && guard.getRecreationLevel() <= 0.4F;
		}
	}
}
