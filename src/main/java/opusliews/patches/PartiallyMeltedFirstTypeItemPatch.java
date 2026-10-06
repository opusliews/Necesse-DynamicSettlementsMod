package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.forge.MeltablePartSystem;

@ModMethodPatch(
		target = Item.class,
		name = "getInventoryFirstItem",
		arguments = {Level.class, PlayerMob.class, InventoryItem.class, Item.Type.class, String.class}
)
public class PartiallyMeltedFirstTypeItemPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(2) InventoryItem item) {
		return MeltablePartSystem.isPartiallyMelted(item);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean blocked,
			@Advice.Return(readOnly = false) Item result
	) {
		if (blocked) result = null;
	}
}
