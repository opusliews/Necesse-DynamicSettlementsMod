package opusliews.item;

import necesse.inventory.item.Item;

public class FirestarterItem extends Item {
	public static final String stringID = "firestarter";

	public FirestarterItem() {
		super(1);
		rarity = Rarity.NORMAL;
		setItemCategory("equipment", "tools");
	}
}
