package opusliews.earlygame;

import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;

public final class CharcoalFuelSystem {
	public static final String charcoalStringID = "charcoal";

	private CharcoalFuelSystem() {
	}

	public static boolean isCharcoal(InventoryItem item) {
		return item != null && charcoalStringID.equals(item.item.getStringID());
	}

	public static int consumeFuel(Inventory inventory, int startSlot, int endSlot, boolean useFuel, int burnTime) {
		if (inventory == null || burnTime <= 0) {
			return 0;
		}

		for (int i = endSlot; i >= startSlot; i--) {
			InventoryItem item = inventory.getItem(i);
			if (!isCharcoal(item)) {
				continue;
			}

			if (useFuel) {
				inventory.addAmount(i, -1);
				if (inventory.getAmount(i) <= 0) {
					inventory.setItem(i, null);
				}
				inventory.markDirty(i);
			}

			return burnTime;
		}

		return 0;
	}
}
