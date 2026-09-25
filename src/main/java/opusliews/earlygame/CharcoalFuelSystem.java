package opusliews.earlygame;

import necesse.engine.registries.GlobalIngredientRegistry;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;

public final class CharcoalFuelSystem {
	public static final String charcoalStringID = "charcoal";

	private CharcoalFuelSystem() {
	}

	public static boolean isCharcoal(InventoryItem item) {
		return item != null && charcoalStringID.equals(item.item.getStringID());
	}


	public static boolean isLog(InventoryItem item) {
		return item != null && item.item.isGlobalIngredient(GlobalIngredientRegistry.getGlobalIngredient("anylog"));
	}

	public static boolean isCookingFuel(InventoryItem item) {
		return isCharcoal(item) || isLog(item);
	}

	public static int getCookingFuelBurnTime(InventoryItem item) {
		if (isCharcoal(item)) return 120000;
		if (isLog(item)) return 60000;
		return 0;
	}

	public static int consumeCookingFuel(Inventory inventory, int startSlot, int endSlot, boolean useFuel) {
		if (inventory == null) return 0;

		for (int i = endSlot; i >= startSlot; i--) {
			InventoryItem item = inventory.getItem(i);
			int burnTime = getCookingFuelBurnTime(item);
			if (burnTime <= 0) continue;

			if (useFuel) {
				inventory.addAmount(i, -1);
				if (inventory.getAmount(i) <= 0) inventory.setItem(i, null);
				inventory.markDirty(i);
			}

			return burnTime;
		}

		return 0;
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
