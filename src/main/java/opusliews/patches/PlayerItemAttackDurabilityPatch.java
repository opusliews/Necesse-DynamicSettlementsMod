package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.inventory.InventoryItem;
import net.bytebuddy.asm.Advice;
import opusliews.durability.DurabilityAction;
import opusliews.durability.DurabilityContext;
import opusliews.durability.ItemDurabilitySystem;

@ModMethodPatch(
		target = PlayerMob.class,
		name = "runItemAttack",
		arguments = {InventoryItem.class, int.class, int.class, int.class, int.class, ItemAttackSlot.class, GNDItemMap.class}
)
public class PlayerItemAttackDurabilityPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This PlayerMob player,
			@Advice.Argument(0) InventoryItem item,
			@Advice.Argument(5) ItemAttackSlot slot
	) {
		if (player.isServer()) {
			ItemDurabilitySystem.applyAction(item, slot, DurabilityAction.ATTACK, DurabilityContext.player(player.getLevel(), player));
		}
	}
}
