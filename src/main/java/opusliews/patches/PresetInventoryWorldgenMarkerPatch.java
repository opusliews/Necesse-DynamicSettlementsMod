package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameRandom;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.LootTablePresets;
import necesse.level.maps.presets.Preset;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenPresetMarker;

@ModMethodPatch(
		target = Preset.class,
		name = "addInventory",
		arguments = {LootTable.class, GameRandom.class, int.class, int.class, Object[].class}
)
public class PresetInventoryWorldgenMarkerPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This Preset preset,
			@Advice.Argument(0) LootTable lootTable,
			@Advice.Argument(2) int tileX,
			@Advice.Argument(3) int tileY
	) {
		WorldgenPresetMarker.addContentTierRefresh(preset, tileX, tileY);
		if (lootTable == LootTablePresets.pirateDisplayStand) {
			WorldgenPresetMarker.addPirateDisplayStandMarker(preset, tileX, tileY);
		}
	}
}
