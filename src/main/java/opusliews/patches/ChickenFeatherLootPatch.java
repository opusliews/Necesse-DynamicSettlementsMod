package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.ChickenMob;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.lootItem.LootItem;
import net.bytebuddy.asm.Advice;

import static opusliews.DSItemRegistry.featherStringID;

@ModMethodPatch(
		target = ChickenMob.class,
		name = "getLootTable",
		arguments = {}
)
public class ChickenFeatherLootPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This ChickenMob chicken,
			@Advice.Return(readOnly = false) LootTable result
	) {
		if (!chicken.isGrown()) {
			return;
		}

		LootTable combined = new LootTable();
		combined.items.addAll(result.items);
		combined.items.add(LootItem.between(featherStringID, 3, 6));
		result = combined;
	}
}
