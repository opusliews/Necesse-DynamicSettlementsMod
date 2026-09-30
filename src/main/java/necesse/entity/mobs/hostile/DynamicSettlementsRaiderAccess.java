package necesse.entity.mobs.hostile;

import necesse.inventory.InventoryItem;

public final class DynamicSettlementsRaiderAccess {
	private DynamicSettlementsRaiderAccess() {
	}

	public static void setCarryingLoot(ItemAttackerRaiderMob raider, InventoryItem item) {
		raider.setCarryingLoot.runAndSend(item);
	}
}
