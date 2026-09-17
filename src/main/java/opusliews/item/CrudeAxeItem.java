package opusliews.item;

import java.util.NoSuchElementException;
import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.axeToolItem.CustomAxeToolItem;
import necesse.inventory.lootTable.presets.ToolsLootTable;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.entity.mobs.PlayerMob;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.TreeObject;
import necesse.level.gameObject.TreeStumpObject;
import necesse.level.maps.Level;
import opusliews.object.PlacedLogObject;
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
		return object.getClass() == TreeObject.class || object instanceof PlacedLogObject || object instanceof TrapdoorObject;
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
