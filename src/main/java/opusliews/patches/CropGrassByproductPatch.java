package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.level.gameObject.SeedObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = SeedObject.class,
		name = "getLootTable",
		arguments = {Level.class, int.class, int.class, int.class}
)
public class CropGrassByproductPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SeedObject crop,
			@Advice.Return LootTable result
	) {
		if (crop.isLastStage()) {
			result.items.add(LootItem.between("grass", 1, 3));
		}
	}
}
