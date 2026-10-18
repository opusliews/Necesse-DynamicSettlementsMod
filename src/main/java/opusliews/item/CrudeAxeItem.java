package opusliews.item;

import java.util.NoSuchElementException;
import necesse.engine.input.Input;
import necesse.engine.input.controller.ControllerInput;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.util.GameMath;
import necesse.inventory.item.toolItem.ToolDamageItem.SmartMineTarget;
import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.axeToolItem.CustomAxeToolItem;
import necesse.inventory.lootTable.presets.ToolsLootTable;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.entity.mobs.PlayerMob;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.GrassObject;
import necesse.level.gameObject.CustomWildFlowerObject;
import necesse.level.gameObject.FlowerPatchObject;
import necesse.level.gameObject.furniture.FlowerObject;
import necesse.level.gameObject.TreeObject;
import necesse.level.gameObject.TreeStumpObject;
import necesse.level.maps.Level;
import opusliews.object.HoleCaveLadderObject;
import opusliews.object.HoleCaveLadderUpObject;
import opusliews.object.PlacedLogObject;
import opusliews.object.PlacedPlankObject;
import opusliews.object.TrapdoorObject;

public class CrudeAxeItem extends CustomAxeToolItem {
	public static final String stringID = "crudeaxe";

	public CrudeAxeItem() {
		super(500, 25, 0.0F, 8, 50, 50, 100, ToolsLootTable.tools);
	}

	@Override
	public boolean canDamageTile(
			Level level,
			int layerID,
			int tileX,
			int tileY,
			ItemAttackerMob attackerMob,
			InventoryItem item
	) {
		if (layerID < 0) {
			return false;
		}

		GameObject object = level.getObject(layerID, tileX, tileY);
		return object instanceof GrassObject
				|| object instanceof CustomWildFlowerObject
				|| object instanceof FlowerPatchObject
				|| object instanceof FlowerObject
				|| object.getClass() == TreeObject.class
				|| object instanceof PlacedLogObject
				|| object instanceof PlacedPlankObject
				|| object instanceof TrapdoorObject
				|| object instanceof HoleCaveLadderObject
				|| object instanceof HoleCaveLadderUpObject;
	}

	/**
	 * Vanilla controller smart mining can snap to objects that our intentionally
	 * restricted crude axe cannot damage. Keep mouse/free-cursor targeting vanilla,
	 * but select an explicitly allowed object when using the directional reticle.
	 */
	@Override
	public SmartMineTarget getFirstSmartHitTile(Level level, PlayerMob player,
			InventoryItem item, int targetX, int targetY) {
		if (!Input.lastInputIsController || ControllerInput.isCursorVisible()) {
			return super.getFirstSmartHitTile(level, player, item, targetX, targetY);
		}
		float dx = targetX - player.x;
		float dy = targetY - player.y;
		float len = (float)Math.sqrt(dx * dx + dy * dy);
		if (len < 0.001F) return null;
		dx /= len;
		dy /= len;
		int range = getMiningRange(item, player);
		SmartMineTarget best = null;
		float bestScore = Float.MAX_VALUE;
		int minX = GameMath.getTileCoordinate(player.x - range - 32);
		int maxX = GameMath.getTileCoordinate(player.x + range + 32);
		int minY = GameMath.getTileCoordinate(player.y - range - 32);
		int maxY = GameMath.getTileCoordinate(player.y + range + 32);
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				if (!level.isTileWithinBounds(x, y)
						|| !isTileInRange(level, x, y, player, null, item)) continue;
				float vx = x * 32 + 16 - player.x;
				float vy = y * 32 + 16 - player.y;
				float forward = vx * dx + vy * dy;
				if (forward <= 0.0F) continue;
				float lateral = Math.abs(vx * dy - vy * dx);
				// Require the aimed line to pass near the tile, avoiding sideways snaps.
				if (lateral > 24.0F) continue;
				for (int layer = 0; layer < ObjectLayerRegistry.getTotalLayers(); layer++) {
					GameObject object = level.getObject(layer, x, y);
					if (object.getID() == 0 || !toolType.canDealDamageTo(object.toolType)
							|| !canDamageTile(level, layer, x, y, player, item)) continue;
					float score = forward + lateral * 2.0F;
					if (score < bestScore) {
						bestScore = score;
						best = new SmartMineTarget(level, x, y, true, layer);
					}
				}
			}
		}
		return best;
	}

	@Override
	protected void runTileDamage(
			Level level,
			int levelX,
			int levelY,
			int priorityObjectLayerID,
			int tileX,
			int tileY,
			PlayerMob player,
			InventoryItem item,
			int damage
	) {
		GameObject originalObject = priorityObjectLayerID >= 0
				? level.getObject(priorityObjectLayerID, tileX, tileY)
				: null;
		int stumpID = getMatchingStumpID(originalObject);

		super.runTileDamage(level, levelX, levelY, priorityObjectLayerID, tileX, tileY, player, item, damage);

		if (!level.isServer() || stumpID < 0) {
			return;
		}

		GameObject currentObject = level.getObject(priorityObjectLayerID, tileX, tileY);
		if (currentObject.getID() != 0) {
			return;
		}

		level.setObject(tileX, tileY, stumpID, 0);
		level.sendObjectUpdatePacket(priorityObjectLayerID, tileX, tileY);
		level.getLevelObject(priorityObjectLayerID, tileX, tileY).checkAround();
	}

	private static int getMatchingStumpID(GameObject object) {
		if (object == null || !object.isTree || object instanceof TreeStumpObject) {
			return -1;
		}

		try {
			int stumpID = ObjectRegistry.getObjectIDRaw(object.getStringID() + "stump");
			return ObjectRegistry.getObject(stumpID) instanceof TreeStumpObject ? stumpID : -1;
		} catch (NoSuchElementException ignored) {
			return -1;
		}
	}
}
