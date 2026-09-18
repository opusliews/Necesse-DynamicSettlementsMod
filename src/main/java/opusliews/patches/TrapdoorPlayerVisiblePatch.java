package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.trapdoor.TrapdoorSystem;
import opusliews.deephole.DeepHoleSystem;

@ModMethodPatch(target = PlayerMob.class, name = "isVisible", arguments = {})
public class TrapdoorPlayerVisiblePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player, @Advice.Return(readOnly = false) boolean result) {
		if (TrapdoorSystem.isTrapdoorHidden(player) || DeepHoleSystem.isFullyHidden(player)) result = false;
	}
}
