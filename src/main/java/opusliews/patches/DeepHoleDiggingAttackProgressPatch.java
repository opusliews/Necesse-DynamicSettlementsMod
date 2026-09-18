package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.deephole.DeepHoleSystem;

@ModMethodPatch(target = PlayerMob.class, name = "getAttackAnimProgress", arguments = {})
public class DeepHoleDiggingAttackProgressPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This PlayerMob player) {
		return DeepHoleSystem.isDigging(player);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This PlayerMob player,
			@Advice.Enter boolean digging,
			@Advice.Return(readOnly = false) float result
	) {
		if (digging) result = DeepHoleSystem.getDiggingAttackProgress(player);
	}
}
