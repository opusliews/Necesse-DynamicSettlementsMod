package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.tile.ShallowHoleSystem;

@ModMethodPatch(
		target = PlayerMob.class,
		name = "runClientInteract",
		arguments = {int.class, int.class, boolean.class}
)
public class ShallowHoleInteractPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This PlayerMob player,
			@Advice.Argument(0) int levelX,
			@Advice.Argument(1) int levelY
	) {
		return ShallowHoleSystem.tryClientDig(player, levelX, levelY);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean handled,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (handled) {
			result = true;
		}
	}
}
