package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.level.gameObject.SeedObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(
		target = SeedObject.class,
		name = "getLootTable",
		arguments = {Level.class, int.class, int.class, int.class}
)
public class HarvestCropDirtRewardPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SeedObject seedObject,
			@Advice.Return LootTable result
	) {
		if (seedObject == null || !seedObject.isLastStage()) return;
		if (result == null) {
			Logging.logMessage("[CropDirtReward] Mature crop returned null loot table object=" + seedObject.getStringID());
			return;
		}

		result.items.add(new LootItem("dirtpile").preventLootMultiplier());
	}
}
