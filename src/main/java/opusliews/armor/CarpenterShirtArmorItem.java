package opusliews.armor;

import necesse.inventory.item.Item;
import necesse.inventory.item.armorItem.ChestArmorItem;
import necesse.inventory.lootTable.presets.CosmeticArmorLootTable;

public class CarpenterShirtArmorItem extends ChestArmorItem {
	public CarpenterShirtArmorItem() {
		super(0, 0, Item.Rarity.COMMON, "carpentershirt", "carpentershirtarms", CosmeticArmorLootTable.cosmeticArmor);
	}
}
