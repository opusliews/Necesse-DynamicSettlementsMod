package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import net.bytebuddy.asm.Advice;
import opusliews.durability.ItemDurabilitySystem;

@ModMethodPatch(
		target = InventoryItem.class,
		name = "draw",
		arguments = {PlayerMob.class, int.class, int.class, boolean.class, boolean.class}
)
public class InventoryItemDurabilityDrawPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This InventoryItem item,
			@Advice.Argument(1) int x,
			@Advice.Argument(2) int y,
			@Advice.Argument(4) boolean inInventory
	) {
		if (inInventory) ItemDurabilitySystem.drawDurabilityBar(item, x, y);
	}
}
