package opusliews.patches;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardDutySystem;
import opusliews.guard.GuardNeedsSystem;

@ModMethodPatch(target = HumanMob.class, name = "serverTick", arguments = {})
public class GuardDutyActivityPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob mob) {
		if (!(mob instanceof GuardHumanMob)) {
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!GuardDutySystem.usesNightSchedule(guard)) {
			return;
		}

		if (!guard.getWorldEntity().isNight()) {
			if (guard.objectUser != null) {
				guard.setActivity("guardduty", 5000, new LocalMessage("activities", "resting"));
			}
			return;
		}

		if (GuardDutySystem.shouldPatrol(guard) && !GuardNeedsSystem.isOnBreak(guard)) {
			guard.setActivity("guardduty", 5000, new LocalMessage("activities", "guardpatrolling"));
		}
	}
}
