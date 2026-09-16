package opusliews.crafting;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.inventory.item.Item;
import necesse.inventory.item.matItem.MatItem;

public class CraftingMaterialItem extends MatItem {
	private final String displayName;

	public CraftingMaterialItem(String displayName, int stackSize, Item.Rarity rarity, String... globalIngredients) {
		super(stackSize, rarity, globalIngredients);
		this.displayName = displayName;
	}

	@Override
	public GameMessage getNewLocalization() {
		return new StaticMessage(displayName);
	}
}
