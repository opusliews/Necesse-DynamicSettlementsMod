package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenLockedContainerSystem;

@ModMethodPatch(
		target = ObjectItem.class,
		name = "onPlaceObject",
		arguments = {GameObject.class, Level.class, int.class, int.class, int.class, int.class, ServerClient.class, InventoryItem.class}
)
public class WorldgenLockedContainerPlacementPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) GameObject object,
			@Advice.Argument(1) Level level,
			@Advice.Argument(2) int objectLayerID,
			@Advice.Argument(3) int tileX,
			@Advice.Argument(4) int tileY,
			@Advice.Argument(7) InventoryItem item,
			@Advice.Return boolean success
	) {
		if (!success) return;
		WorldgenLockedContainerSystem.restorePlacedLockedContainer(level, objectLayerID, tileX, tileY, object, item);
	}
}
