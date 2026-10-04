package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.inventory.lootTable.lootItem.OneOfTicketLootItems;
import necesse.inventory.lootTable.presets.IncursionCrateLootTable;
import necesse.inventory.lootTable.presets.IncursionLootLists;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(target = IncursionCrateLootTable.class, name = "<init>", arguments = {})
public class IncursionCrateLootTableRemoveTorchPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This IncursionCrateLootTable table) {
		if (table == null || table.oneOfItems == null) {
			Logging.logMessage("[CrateLoot] Failed to remove Torch from incursion crate loot: table or oneOfItems was null.");
			return;
		}

		table.items.remove(table.oneOfItems);
		table.oneOfItems = new OneOfTicketLootItems(
				100, LootItem.offset("firearrow", 10, 5),
				100, LootItem.offset("ironarrow", 10, 5),
				100, LootItem.offset("bonearrow", 10, 5),
				50, LootItem.between("greaterhealthpotion", 1, 1),
				25, LootItem.between("greatermanapotion", 1, 2),
				10, new LootItem("teleportationscroll"),
				35, IncursionLootLists.greaterPotions,
				15, IncursionCrateLootTable.potions
		);
		table.items.add(table.oneOfItems);
		Logging.logMessage("[CrateLoot] Removed Torch from incursion crate loot table.");
	}
}
