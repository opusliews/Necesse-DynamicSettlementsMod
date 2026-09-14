package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardFatigueSystem;
import opusliews.logging.Logging;

@ModMethodPatch(target = Mob.class, name = "isServerHit", arguments = {GameDamage.class, float.class, float.class, float.class, Attacker.class})
public class GuardFatigueCombatPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This Mob mob, @Advice.Argument(4) Attacker attacker) {
		if (!(mob instanceof GuardHumanMob) || attacker == null) {
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		Mob attackOwner = attacker.getAttackOwner();
		if (attackOwner != null && attackOwner != mob) {
			Logging.logMessage("GuardFatigueDebug: isServerHit guard=" + guard.getUniqueID()
					+ " attacker=" + attackOwner.getStringID() + "#" + attackOwner.getUniqueID()
					+ " scheduledRest=" + GuardFatigueSystem.isScheduledRestPeriod(guard));
			GuardFatigueSystem.registerDirectRestAttacker(guard, attackOwner);
		}
	}
}
