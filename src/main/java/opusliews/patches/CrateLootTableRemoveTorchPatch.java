package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.lootTable.LootItemInterface;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.inventory.lootTable.lootItem.OneOfTicketLootItems;
import necesse.inventory.lootTable.presets.CrateLootTable;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(target = CrateLootTable.class, name = "<init>", arguments = {LootItemInterface.class})
public class CrateLootTableRemoveTorchPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This CrateLootTable table, @Advice.Argument(0) LootItemInterface bars) {
		if (table == null || table.oneOfItems == null) {
			Logging.logMessage("[CrateLoot] Failed to remove Torch from surface crate loot: table or oneOfItems was null.");
			return;
		}

		table.items.remove(table.oneOfItems);
		table.oneOfItems = new OneOfTicketLootItems(
				100, LootItem.offset("stonearrow", 10, 5),
				100, LootItem.offset("firearrow", 10, 5),
				100, LootItem.offset("ironarrow", 10, 5),
				75, LootItem.between("healthpotion", 1, 2),
				50, LootItem.between("manapotion", 1, 2),
				100, LootItem.offset("ninjastar", 8, 3),
				25, new LootItem("recallscroll"),
				50, bars,
				50, CrateLootTable.potions
		);
		table.items.add(table.oneOfItems);
		Logging.logMessage("[CrateLoot] Removed Torch from surface crate loot table.");
	}
}
