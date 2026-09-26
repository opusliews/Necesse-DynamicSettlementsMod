package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.container.Container;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.InventoryCraftingTime;

@ModMethodPatch(
		target = Container.class,
		name = "applyCraftingAction",
		arguments = {int.class, int.class, int.class, boolean.class}
)
public class InventoryCraftingActionPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This Container container,
			@Advice.Argument(0) int recipeID,
			@Advice.Argument(1) int recipeHash,
			@Advice.Argument(2) int craftAmount,
			@Advice.Argument(3) boolean transferToInventory,
			@Advice.Local("inventoryCraftResult") int inventoryCraftResult
	) {
		inventoryCraftResult = InventoryCraftingTime.tryStartCraft(
				container,
				recipeID,
				recipeHash,
				craftAmount,
				transferToInventory
		);
		return inventoryCraftResult >= 0;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean handled,
			@Advice.Local("inventoryCraftResult") int inventoryCraftResult,
			@Advice.Return(readOnly = false) int result
	) {
		if (handled) result = inventoryCraftResult;
	}
}
