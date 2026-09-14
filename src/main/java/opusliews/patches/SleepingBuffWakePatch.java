package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.MobWasHitEvent;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.staticBuffs.SleepingBuff;
import net.bytebuddy.asm.Advice;
import opusliews.sleep.SleepAttackWakeSystem;

@ModMethodPatch(
		target = SleepingBuff.class,
		name = "onWasHit",
		arguments = {ActiveBuff.class, MobWasHitEvent.class}
)
public class SleepingBuffWakePatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.Argument(0) ActiveBuff buff,
			@Advice.Argument(1) MobWasHitEvent event
	) {
		SleepAttackWakeSystem.onPlayerHit(buff.owner, event, "SleepingBuff.onWasHit");
	}
}
