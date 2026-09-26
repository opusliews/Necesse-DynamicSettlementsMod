package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenLockedContainerSystem;

@ModMethodPatch(
		target = Item.class,
		name = "canCombineItem",
		arguments = {Level.class, PlayerMob.class, InventoryItem.class, InventoryItem.class, String.class}
)
public class WorldgenLockedContainerItemCombinePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(2) InventoryItem me,
			@Advice.Argument(3) InventoryItem them,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (!result) return;
		if (WorldgenLockedContainerSystem.isPackedLockedContainer(me) || WorldgenLockedContainerSystem.isPackedLockedContainer(them)) result = false;
	}
}
