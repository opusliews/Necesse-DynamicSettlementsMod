package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.InventoryCraftingTime;

@ModMethodPatch(target = PlayerMob.class, name = "setInventoryExtended", arguments = {boolean.class})
public class InventoryCraftingClosePatch {
	@Advice.OnMethodEnter
	public static boolean onEnter(@Advice.This PlayerMob player) {
		return player.isInventoryExtended();
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This PlayerMob player,
			@Advice.Argument(0) boolean extended,
			@Advice.Enter boolean wasExtended
	) {
		if (wasExtended && !extended) InventoryCraftingTime.onInventoryClosed(player);
	}
}
