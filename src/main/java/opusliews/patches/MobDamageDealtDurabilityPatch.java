package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.MobWasHitEvent;
import necesse.entity.mobs.PlayerInventoryItemAttackSlot;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.GameDamage;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import net.bytebuddy.asm.Advice;
import opusliews.durability.DurabilityAction;
import opusliews.durability.DurabilityContext;
import opusliews.durability.ItemDurabilitySystem;

@ModMethodPatch(
		target = Mob.class,
		name = "isServerHit",
		arguments = {GameDamage.class, float.class, float.class, float.class, Attacker.class}
)
public class MobDamageDealtDurabilityPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This Mob target,
			@Advice.Argument(4) Attacker attacker,
			@Advice.Return MobWasHitEvent result
	) {
		if (attacker == null || result == null || result.wasPrevented || result.damage <= 0) return;

		PlayerMob player = attacker.getFirstPlayerOwner();
		if (player == null || !player.isServer()) return;

		PlayerInventorySlot attackSlot = player.attackSlot;
		if (attackSlot == null) attackSlot = player.getSelectedItemSlot();
		InventoryItem item = attackSlot.getItem(player.getInv());
		if (item == null) return;

		ItemDurabilitySystem.applyAction(
				item,
				new PlayerInventoryItemAttackSlot(player, attackSlot),
				DurabilityAction.DAMAGE_DEALT,
				DurabilityContext.mob(player.getLevel(), player, target)
		);
	}
}
