package opusliews.worldgengating;

import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.armorItem.ArmorItem;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.inventory.item.toolItem.ToolItem;
import necesse.inventory.item.toolItem.ToolType;

import java.util.Collection;

public final class WorldgenContentTierResolver {
	private WorldgenContentTierResolver() {
	}

	public static WorldgenLootTier getHighestTier(Inventory inventory) {
		if (inventory == null) return null;
		WorldgenLootTier highest = null;
		for (int slot = 0; slot < inventory.getSize(); slot++) highest = WorldgenLootTier.max(highest, getItemTier(inventory.getItem(slot)));
		return highest;
	}

	public static WorldgenLootTier getHighestTier(Collection<InventoryItem> items) {
		if (items == null) return null;
		WorldgenLootTier highest = null;
		for (InventoryItem item : items) highest = WorldgenLootTier.max(highest, getItemTier(item));
		return highest;
	}

	public static WorldgenLootTier getItemTier(InventoryItem item) {
		if (item == null || item.item == null) return null;

		String itemStringID = item.item.getStringID();

		WorldgenLootTier toolMapped = WorldgenToolTierMap.getTier(itemStringID);
		if (toolMapped != null) return toolMapped;

		WorldgenLootTier equipmentMapped = WorldgenEquipmentTierMap.getTier(itemStringID);
		if (equipmentMapped != null) return equipmentMapped;

		if (item.item instanceof ToolDamageItem) {
			ToolDamageItem tool = (ToolDamageItem)item.item;
			if (tool.getToolType(item) != ToolType.NONE) {
				int toolTier = (int)Math.ceil(tool.getToolTier(item, null));
				return WorldgenLootTier.fromRequiredToolTier(toolTier);
			}
		}

		if (item.item instanceof ToolItem) return fromEquipmentValue(((ToolItem)item.item).getEnchantCost(item));
		if (item.item instanceof ArmorItem) return fromEquipmentValue(((ArmorItem)item.item).getEnchantCost(item));
		return null;
	}

	public static WorldgenLootTier fromEquipmentValue(int value) {
		if (value <= 0) return null;
		if (value <= 650) return WorldgenLootTier.DEMONIC;
		if (value <= 800) return WorldgenLootTier.RUNIC;
		if (value <= 950) return WorldgenLootTier.IVY;
		if (value <= 1200) return WorldgenLootTier.QUARTZ;
		if (value <= 1400) return WorldgenLootTier.TUNGSTEN;
		if (value <= 1525) return WorldgenLootTier.GLACIAL;
		if (value <= 1575) return WorldgenLootTier.DRYAD;
		if (value <= 1700) return WorldgenLootTier.MYCELIUM;
		return WorldgenLootTier.FOSSIL;
	}
}
