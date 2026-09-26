package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.container.Container;
import necesse.inventory.recipe.Recipe;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.InventoryCraftingTime;
import opusliews.worldgengating.WorldgenStationProgressionSystem;

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
			@Advice.This Container container,
			@Advice.Argument(0) int recipeID,
			@Advice.Enter boolean handled,
			@Advice.Local("inventoryCraftResult") int inventoryCraftResult,
			@Advice.Return(readOnly = false) int result
	) {
		if (handled) {
			result = inventoryCraftResult;
			return;
		}
		if (result <= 0 || container == null || container.client == null || !container.client.isServer()) return;
		Recipe recipe = container.getRecipe(recipeID);
		if (recipe != null) WorldgenStationProgressionSystem.recordCraftedRecipe(container.client.playerMob, recipe);
	}
}
