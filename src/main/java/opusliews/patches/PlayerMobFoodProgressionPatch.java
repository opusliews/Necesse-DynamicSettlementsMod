package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.item.placeableItem.consumableItem.food.FoodConsumableItem;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;

@ModMethodPatch(target = PlayerMob.class, name = "useFoodItem", arguments = {FoodConsumableItem.class, boolean.class})
public class PlayerMobFoodProgressionPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player, @Advice.Argument(0) FoodConsumableItem item, @Advice.Return boolean used) {
		if (used) EarlyHealthProgressionSystem.onFoodConsumed(player, item);
	}
}
