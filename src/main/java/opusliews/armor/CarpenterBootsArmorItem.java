package opusliews.armor;

import necesse.inventory.item.Item;
import necesse.inventory.item.armorItem.BootsArmorItem;
import necesse.inventory.lootTable.presets.CosmeticArmorLootTable;

public class CarpenterBootsArmorItem extends BootsArmorItem {
	public CarpenterBootsArmorItem() {
		super(0, 0, Item.Rarity.COMMON, "carpenterboots", CosmeticArmorLootTable.cosmeticArmor);
	}
}
