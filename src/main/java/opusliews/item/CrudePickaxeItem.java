package opusliews.item;

import necesse.inventory.item.toolItem.pickaxeToolItem.CustomPickaxeToolItem;
import necesse.inventory.lootTable.presets.ToolsLootTable;

public class CrudePickaxeItem extends CustomPickaxeToolItem {
	public static final String stringID = "crudepickaxe";

	public CrudePickaxeItem() {
		super(500, 25, 0.0F, 8, 50, 50, 100, ToolsLootTable.tools);
	}
}
