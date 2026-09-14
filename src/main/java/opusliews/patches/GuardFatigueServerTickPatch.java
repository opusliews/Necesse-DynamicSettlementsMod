package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardFatigueSystem;

@ModMethodPatch(target = HumanMob.class, name = "serverTick", arguments = {})
public class GuardFatigueServerTickPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob mob) {
		if (mob instanceof GuardHumanMob) {
			GuardFatigueSystem.tickGuard((GuardHumanMob)mob);
		}
	}
}
