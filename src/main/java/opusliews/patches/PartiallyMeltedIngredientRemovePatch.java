package opusliews.patches;

import java.util.Collection;
import java.util.function.Predicate;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.recipe.Ingredient;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.forge.MeltablePartSystem;

@ModMethodPatch(
		target = Item.class,
		name = "removeInventoryAmount",
		arguments = {Level.class, PlayerMob.class, InventoryItem.class, Inventory.class, int.class, Ingredient.class, Predicate.class, int.class, Collection.class}
)
public class PartiallyMeltedIngredientRemovePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(2) InventoryItem item) {
		return MeltablePartSystem.isPartiallyMelted(item);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean blocked,
			@Advice.Return(readOnly = false) int result
	) {
		if (blocked) result = 0;
	}
}
