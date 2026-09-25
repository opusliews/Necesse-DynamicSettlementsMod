package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;

@ModMethodPatch(target = PlayerMob.class, name = "addHunger", arguments = {float.class})
public class PlayerMobAddHungerCapacityPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This PlayerMob player, @Advice.Argument(value = 0, readOnly = false) float amount) {
		amount = EarlyHealthProgressionSystem.scaleHungerAmount(player, amount);
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player) {
		player.hungerLevel = Math.min(1.0F, player.hungerLevel);
	}
}
