package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.lootItem.LootItem;
import necesse.level.gameObject.SurfaceGrassObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = SurfaceGrassObject.class,
		name = "getLootTable",
		arguments = {Level.class, int.class, int.class, int.class}
)
public class SurfaceGrassLootPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) Level level,
			@Advice.Argument(2) int tileX,
			@Advice.Argument(3) int tileY,
			@Advice.Return(readOnly = false) LootTable result
	) {
		if (level.objectLayer.isPlayerPlaced(tileX, tileY)) {
			return;
		}

		LootTable combined = new LootTable();
		combined.items.addAll(result.items);
		combined.items.add(new LootItem("grass"));
		result = combined;
	}
}
