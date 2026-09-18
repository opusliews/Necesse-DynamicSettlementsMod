package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.deephole.DeepHoleSystem;
import opusliews.trapdoor.TrapdoorSystem;

@ModMethodPatch(target = PlayerMob.class, name = "tickMovement", arguments = {float.class})
public class TrapdoorPlayerMovementPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This PlayerMob player) {
		DeepHoleSystem.tick(player);
		if (!TrapdoorSystem.isHidden(player) && !DeepHoleSystem.isDigging(player)) return;
		player.moveX = 0.0F;
		player.moveY = 0.0F;
		player.dx = 0.0F;
		player.dy = 0.0F;
	}
}
