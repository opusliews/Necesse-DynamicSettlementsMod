package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenLockedContainerSystem;

import java.util.ArrayList;

@ModMethodPatch(
		target = GameObject.class,
		name = "getEntityDroppedItems",
		arguments = {Level.class, int.class, int.class, int.class, String.class}
)
public class WorldgenLockedContainerEntityDropPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int objectLayerID,
			@Advice.Argument(2) int tileX,
			@Advice.Argument(3) int tileY,
			@Advice.Argument(4) String purpose,
			@Advice.Return(readOnly = false) ArrayList<InventoryItem> result
	) {
		result = WorldgenLockedContainerSystem.suppressLockedContainerEntityDrops(level, objectLayerID, tileX, tileY, purpose, result);
	}
}
