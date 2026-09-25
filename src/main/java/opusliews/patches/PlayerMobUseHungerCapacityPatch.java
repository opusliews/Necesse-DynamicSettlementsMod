package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;

@ModMethodPatch(target = PlayerMob.class, name = "useHunger", arguments = {float.class, boolean.class})
public class PlayerMobUseHungerCapacityPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This PlayerMob player, @Advice.Argument(value = 0, readOnly = false) float amount) {
		amount = EarlyHealthProgressionSystem.scaleHungerAmount(player, amount);
	}
}
