package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerInventoryItemAttackSlot;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opusliews.durability.DurabilityAction;
import opusliews.durability.DurabilityContext;
import opusliews.durability.ItemDurabilitySystem;

@ModMethodPatch(target = LevelObject.class, name = "interact", arguments = {PlayerMob.class})
public class LevelObjectDurabilityInteractPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This LevelObject levelObject, @Advice.Argument(0) PlayerMob player) {
		if (!player.isServer()) return;

		PlayerInventorySlot selectedSlot = player.getSelectedItemSlot();
		InventoryItem item = selectedSlot.getItem(player.getInv());
		if (item == null) return;

		ItemDurabilitySystem.applyAction(
				item,
				new PlayerInventoryItemAttackSlot(player, selectedSlot),
				DurabilityAction.OBJECT_INTERACT,
				DurabilityContext.object(player.getLevel(), player, levelObject)
		);
	}
}
