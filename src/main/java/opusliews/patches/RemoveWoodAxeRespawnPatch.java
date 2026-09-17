package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.InventoryAddConsumer;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventoryManager;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = PlayerInventoryManager.class,
		name = "addItem",
		arguments = {InventoryItem.class, boolean.class, String.class, InventoryAddConsumer.class}
)
public class RemoveWoodAxeRespawnPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(0) InventoryItem item,
			@Advice.Argument(2) String purpose
	) {
		return item != null
				&& "woodaxe".equals(item.item.getStringID())
				&& "respawnitem".equals(purpose);
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
