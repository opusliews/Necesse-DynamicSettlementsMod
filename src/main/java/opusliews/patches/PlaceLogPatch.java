package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.object.PlacedLogRegistry;

@ModMethodPatch(
		target = PlayerMob.class,
		name = "runClientInteract",
		arguments = {int.class, int.class, boolean.class}
)
public class PlaceLogPatch {
	private static final double placeRange = 100.0;

	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This PlayerMob player,
			@Advice.Argument(0) int levelX,
			@Advice.Argument(1) int levelY
	) {
		Level level = player.getLevel();
		if (level == null || !level.isClient()) {
			return false;
		}

		InventoryItem item = player.getSelectedItem();
		if (item == null || !item.item.isGlobalIngredient("anylog")) {
			return false;
		}

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);

		if (!level.isTileWithinBounds(tileX, tileY)) {
			return true;
		}

		int placedObjectID = PlacedLogRegistry.getObjectID(item.item.getStringID());
		if (placedObjectID < 0) {
			return false;
		}

		GameObject placedObject = ObjectRegistry.getObject(placedObjectID);
		if (placedObject.canPlace(level, 0, tileX, tileY, 0, true, false) != null) {
			return true;
		}

		double distance = player.getPositionPoint().distance(
				tileX * 32.0 + 16.0,
				tileY * 32.0 + 16.0
		);

		if (distance > placeRange) {
			return true;
		}

		placedObject.playPlaceSound(tileX, tileY);

		level.getClient().network.sendPacket(
				new opusliews.network.PacketPlaceLog(tileX, tileY)
		);

		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean handled,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (handled) {
			result = true;
		}
	}
}