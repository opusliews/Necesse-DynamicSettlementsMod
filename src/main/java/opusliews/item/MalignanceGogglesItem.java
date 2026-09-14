package opusliews.item;

import necesse.inventory.item.Item;
import necesse.inventory.item.trinketItem.SimpleTrinketItem;

public class MalignanceGogglesItem extends SimpleTrinketItem {
	public static final String buffStringID = "malignancegogglesbuff";

	public MalignanceGogglesItem() {
		super(Item.Rarity.UNCOMMON, buffStringID, 100, null);
	}
}
