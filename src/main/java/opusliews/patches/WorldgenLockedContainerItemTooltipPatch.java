package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenLockedContainerSystem;

@ModMethodPatch(
		target = ObjectItem.class,
		name = "getTooltips",
		arguments = {InventoryItem.class, PlayerMob.class, GameBlackboard.class}
)
public class WorldgenLockedContainerItemTooltipPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) InventoryItem item,
			@Advice.Return ListGameTooltips result
	) {
		WorldgenLockedContainerSystem.addPackedItemTooltips(item, result);
	}
}
