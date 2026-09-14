package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardFatigueSystem;

@ModMethodPatch(target = GameDamage.class, name = "getTotalDamage", arguments = {Mob.class, Attacker.class, float.class})
public class GuardFatigueDealtDamagePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(1) Attacker attacker,
			@Advice.Return(readOnly = false) int result
	) {
		if (attacker == null) {
			return;
		}

		Mob owner = attacker.getAttackOwner();
		if (owner instanceof GuardHumanMob) {
			result = Math.max(0, Math.round(result * GuardFatigueSystem.getOutgoingDamageMultiplier((GuardHumanMob)owner)));
		}
	}
}
