package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardFatigueSystem;

@ModMethodPatch(target = Mob.class, name = "getIncomingDamageModifier", arguments = {})
public class GuardFatigueIncomingDamagePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This Mob mob, @Advice.Return(readOnly = false) float result) {
		if (mob instanceof GuardHumanMob) {
			result *= GuardFatigueSystem.getIncomingDamageMultiplier((GuardHumanMob)mob);
		}
	}
}
