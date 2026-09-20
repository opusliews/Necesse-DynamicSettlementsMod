package opusliews.crafting;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.inventory.item.Item;
import necesse.inventory.item.matItem.MatItem;

public class CraftingMaterialItem extends MatItem {
	public CraftingMaterialItem(int stackSize, Item.Rarity rarity, String... globalIngredients) {
		super(stackSize, rarity, globalIngredients);
	}

	@Override
	public GameMessage getNewLocalization() {
		return new LocalMessage("item", getStringID());
	}
}
