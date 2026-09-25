package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventory;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.item.toolItem.axeToolItem.AxeToolItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.network.PacketCutPlacedPlank;
import opusliews.network.PacketPlacePlank;
import opusliews.object.PlacedPlankObject;
import opusliews.object.PlacedPlankRegistry;

@ModMethodPatch(
		target = PlayerMob.class,
		name = "runClientInteract",
		arguments = {int.class, int.class, boolean.class}
)
public class PlacePlankPatch {
	public static final double placeRange = 100.0;

	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This PlayerMob player,
			@Advice.Argument(0) int levelX,
			@Advice.Argument(1) int levelY
	) {
		Level level = player.getLevel();
		if (level == null || !level.isClient()) return false;

		InventoryItem item = player.getSelectedItem();
		if (item == null) return false;

		int tileX = GameMath.getTileCoordinate(levelX);
		int tileY = GameMath.getTileCoordinate(levelY);
		if (!level.isTileWithinBounds(tileX, tileY)) return false;

		double distance = player.getPositionPoint().distance(tileX * 32.0 + 16.0, tileY * 32.0 + 16.0);

		if (item.item instanceof AxeToolItem
				&& level.getObject(ObjectLayerRegistry.TILE_LAYER, tileX, tileY) instanceof PlacedPlankObject) {
			if (distance > placeRange) return true;

			level.getClient().network.sendPacket(new PacketCutPlacedPlank(tileX, tileY));
			return true;
		}

		if (!item.item.isGlobalIngredient("dsanyplank")) return false;
		if (level.isProtected(tileX, tileY)) return true;

		int placedObjectID = PlacedPlankRegistry.getObjectID(item.item.getStringID());
		if (placedObjectID < 0) return false;

		int layerID = ObjectLayerRegistry.TILE_LAYER;
		GameObject placedObject = ObjectRegistry.getObject(placedObjectID);
		if (placedObject.canPlace(level, layerID, tileX, tileY, 0, true, false) != null) return true;

		if (distance > placeRange) return true;

		placedObject.playPlaceSound(tileX, tileY);
		level.getClient().network.sendPacket(new PacketPlacePlank(tileX, tileY));

		PlayerInventorySlot selectedSlot = player.getSelectedItemSlot();
		PlayerInventory inventory = selectedSlot.getInv(player.getInv());
		if (inventory != null) {
			inventory.setAmount(selectedSlot.slot, item.getAmount() - 1);
		}
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean handled,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (handled) result = true;
	}
}
