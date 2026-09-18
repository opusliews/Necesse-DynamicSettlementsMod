package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.PlayerInventorySlot;
import net.bytebuddy.asm.Advice;
import opusliews.trapdoor.TrapdoorSystem;
import opusliews.deephole.DeepHoleSystem;

@ModMethodPatch(target = PlayerMob.class, name = "runClientAttack", arguments = {PlayerInventorySlot.class, int.class, int.class})
public class TrapdoorPlayerAttackPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This PlayerMob player) {
		return TrapdoorSystem.isHidden(player) || DeepHoleSystem.isDigging(player);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean blocked,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (blocked) result = false;
	}
}
