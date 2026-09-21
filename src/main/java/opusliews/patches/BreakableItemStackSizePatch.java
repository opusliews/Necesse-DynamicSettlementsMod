package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import net.bytebuddy.asm.Advice;
import opusliews.durability.ItemDurabilitySystem;

@ModMethodPatch(
		target = Inventory.class,
		name = "getItemStackLimit",
		arguments = {int.class, InventoryItem.class}
)
public class BreakableItemStackSizePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(1) InventoryItem item,
			@Advice.Return(readOnly = false) int result
	) {
		if (ItemDurabilitySystem.isDamaged(item)) result = Math.min(result, 1);
	}
}
