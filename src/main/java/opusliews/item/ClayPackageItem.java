package opusliews.item;

import necesse.inventory.item.Item;
import necesse.inventory.item.matItem.MatItem;

/**
 * Internal-only work-inventory container for clay firing materials.
 * The actual contents are stored on the InventoryItem GND by ClayPackageSystem.
 */
public class ClayPackageItem extends MatItem {
	public ClayPackageItem() {
		super(1, Item.Rarity.NORMAL);
	}
}
