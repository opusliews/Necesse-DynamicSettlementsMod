package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.item.Item;
import net.bytebuddy.asm.Advice;
import opusliews.durability.ItemDurabilityRegistry;

@ModMethodPatch(
		target = Item.class,
		name = "getStackSize",
		arguments = {}
)
public class BreakableItemStackSizePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This Item item,
			@Advice.Return(readOnly = false) int result
	) {
		if (ItemDurabilityRegistry.isBreakable(item)) result = 1;
	}
}
