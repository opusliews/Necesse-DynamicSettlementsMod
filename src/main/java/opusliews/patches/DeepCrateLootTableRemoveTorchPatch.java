package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.lootTable.LootItemInterface;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.inventory.lootTable.lootItem.OneOfTicketLootItems;
import necesse.inventory.lootTable.presets.DeepCrateLootTable;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(target = DeepCrateLootTable.class, name = "<init>", arguments = {LootItemInterface.class})
public class DeepCrateLootTableRemoveTorchPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This DeepCrateLootTable table, @Advice.Argument(0) LootItemInterface bars) {
		if (table == null || table.oneOfItems == null) {
			Logging.logMessage("[CrateLoot] Failed to remove Torch from deep crate loot: table or oneOfItems was null.");
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
				50, bars,
				50, DeepCrateLootTable.potions
		);
		table.items.add(table.oneOfItems);
		Logging.logMessage("[CrateLoot] Removed Torch from deep crate loot table.");
	}
}
