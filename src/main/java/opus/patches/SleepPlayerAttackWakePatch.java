package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.MobWasHitEvent;
import net.bytebuddy.asm.Advice;
import opus.sleep.SleepAttackWakeSystem;

@ModMethodPatch(
		target = Mob.class,
		name = "isHit",
		arguments = {MobWasHitEvent.class, Attacker.class}
)
public class SleepPlayerAttackWakePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This Mob mob,
			@Advice.Argument(0) MobWasHitEvent event
	) {
		SleepAttackWakeSystem.onPlayerHit(mob, event, "Mob.isHit");
	}
}
