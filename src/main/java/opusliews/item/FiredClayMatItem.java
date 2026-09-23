package opusliews.item;

import necesse.inventory.item.Item;
import necesse.inventory.item.matItem.MatItem;

/**
 * Marker item for fired outputs that belong to the clay firing system.
 * These items are eligible for Clay Package transport just like FireableMatItem inputs.
 */
public class FiredClayMatItem extends MatItem {
	public FiredClayMatItem(int stackSize, Item.Rarity rarity) {
		super(stackSize, rarity);
	}
}
