package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.entity.mobs.Mob;
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
		name = "runItemMobInteract",
		arguments = {ItemInteractAction.class, InventoryItem.class, int.class, int.class, Mob.class, int.class, ItemAttackSlot.class, GNDItemMap.class}
)
public class PlayerItemMobInteractDurabilityPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This PlayerMob player,
			@Advice.Argument(1) InventoryItem item,
			@Advice.Argument(4) Mob targetMob,
			@Advice.Argument(6) ItemAttackSlot slot
	) {
		if (player.isServer()) {
			ItemDurabilitySystem.applyAction(item, slot, DurabilityAction.MOB_INTERACT, DurabilityContext.mob(player.getLevel(), player, targetMob));
		}
	}
}
