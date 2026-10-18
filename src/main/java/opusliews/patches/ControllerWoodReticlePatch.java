package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.object.PlacedLogRegistry;
import opusliews.object.PlacedPlankRegistry;

import java.awt.*;

/** Make the normal red controller reticle show the actual log/plank placement tile. */
@ModMethodPatch(target = Item.class, name = "getControllerAttackLevelPos",
		arguments = {Level.class, float.class, float.class, PlayerMob.class, InventoryItem.class})
public class ControllerWoodReticlePatch {
	@Advice.OnMethodExit
	public static void exit(@Advice.This Item item, @Advice.Argument(0) Level level,
			@Advice.Argument(1) float aimX, @Advice.Argument(2) float aimY,
			@Advice.Argument(3) PlayerMob player, @Advice.Return(readOnly = false) Point result) {
		if (level == null || item == null || player == null) return;
		boolean log = item.isGlobalIngredient("anylog");
		if (!log && !item.isGlobalIngredient("dsanyplank")) return;
		int objectID = log ? PlacedLogRegistry.getObjectID(item.getStringID())
				: PlacedPlankRegistry.getObjectID(item.getStringID());
		if (objectID < 0) return;
		float length = (float)Math.sqrt(aimX * aimX + aimY * aimY);
		if (length < 0.001F) return;
		// Let the hole reticle patch target pits before ordinary log placement.
		if (ControllerHoleActions.Handler.findHole(level, player, aimX, aimY) != null) return;
		GameObject placed = ObjectRegistry.getObject(objectID);
		int layer = log ? 0 : ObjectLayerRegistry.TILE_LAYER;
		Point target = ControllerWoodActionsPatch.ControllerWoodActions.findPlacement(
				level, player, aimX / length, aimY / length, placed, layer);
		if (target != null) result = target;
	}
}
