package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryAddConsumer;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = Inventory.class,
		name = "addItem",
		arguments = {Level.class, PlayerMob.class, InventoryItem.class, String.class, InventoryAddConsumer.class}
)
public class RemoveStarterItemsPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(2) InventoryItem item,
			@Advice.Argument(3) String purpose
	) {
		return item != null
				&& (
						"woodaxe".equals(item.item.getStringID())
						|| "woodsword".equals(item.item.getStringID())
				)
				&& "startitem".equals(purpose);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean skipped,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (skipped) {
			result = true;
		}
	}
}
