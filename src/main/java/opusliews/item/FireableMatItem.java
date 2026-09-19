package opusliews.item;

import necesse.inventory.item.Item;
import necesse.inventory.item.matItem.MatItem;

public class FireableMatItem extends MatItem {
	private final String firedItemStringID;

	public FireableMatItem(int stackSize, Item.Rarity rarity, String firedItemStringID) {
		super(stackSize, rarity);
		this.firedItemStringID = firedItemStringID;
	}

	public String getFiredItemStringID() {
		return firedItemStringID;
	}
}
