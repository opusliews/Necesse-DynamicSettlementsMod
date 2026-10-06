package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.recipe.IngredientCounter;
import necesse.inventory.recipe.IngredientStep;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.forge.MeltablePartSystem;

@ModMethodPatch(
		target = Item.class,
		name = "countIngredientAmount",
		arguments = {Level.class, PlayerMob.class, Inventory.class, int.class, InventoryItem.class, String.class, IngredientCounter.class}
)
public class PartiallyMeltedIngredientCountPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(4) InventoryItem item) {
		return MeltablePartSystem.isPartiallyMelted(item);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean blocked,
			@Advice.Return(readOnly = false) IngredientStep result
	) {
		if (blocked) result = IngredientStep.CONTINUE;
	}
}
