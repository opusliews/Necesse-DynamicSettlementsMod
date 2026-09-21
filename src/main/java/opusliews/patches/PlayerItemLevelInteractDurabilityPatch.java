package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.ItemInteractAction;
import net.bytebuddy.asm.Advice;
import opusliews.durability.DurabilityAction;
import opusliews.durability.DurabilityContext;
import opusliews.durability.ItemDurabilitySystem;

@ModMethodPatch(
		target = PlayerMob.class,
		name = "runItemLevelInteract",
		arguments = {ItemInteractAction.class, InventoryItem.class, int.class, int.class, int.class, ItemAttackSlot.class, GNDItemMap.class}
)
public class PlayerItemLevelInteractDurabilityPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This PlayerMob player,
			@Advice.Argument(1) InventoryItem item,
			@Advice.Argument(5) ItemAttackSlot slot
	) {
		if (player.isServer()) {
			ItemDurabilitySystem.applyAction(item, slot, DurabilityAction.LEVEL_INTERACT, DurabilityContext.player(player.getLevel(), player));
		}
	}
}
