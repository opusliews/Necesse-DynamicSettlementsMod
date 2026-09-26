package opusliews.logging;

import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.InventoryObjectEntity;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;

public final class InventoryPersistenceDebug {
	private InventoryPersistenceDebug() {
	}

	public static void logPlayer(String stage, PlayerMob player) {
		if (!Logging.logEnabled || player == null || player.getInv() == null) return;
		Logging.logMessage("[InventoryPersistence] " + stage
				+ " player=" + player.playerName
				+ " main=" + inventorySummary(player.getInv().main)
				+ " drag=" + inventorySummary(player.getInv().drag)
				+ " armor=" + inventorySummary(player.getInv().equipment.getSelectedArmorInventory()));
	}

	public static void logPlayerPlacedContainer(String stage, InventoryObjectEntity entity) {
		if (!Logging.logEnabled || entity == null || entity.getLevel() == null) return;
		if (!entity.getLevel().objectLayer.isPlayerPlaced(0, entity.tileX, entity.tileY)) return;
		Inventory inventory = entity.getInventory();
		if (inventory == null || isEmpty(inventory)) return;
		String objectID = entity.getLevel().getObject(entity.tileX, entity.tileY).getStringID();
		Logging.logMessage("[InventoryPersistence] " + stage
				+ " container=" + objectID
				+ " level=" + entity.getLevel().getIdentifier()
				+ " tile=" + entity.tileX + "," + entity.tileY
				+ " items=" + inventorySummary(inventory));
	}

	private static boolean isEmpty(Inventory inventory) {
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			if (inventory.getItem(slot) != null) return false;
		}
		return true;
	}

	private static String inventorySummary(Inventory inventory) {
		if (inventory == null) return "<null>";
		StringBuilder out = new StringBuilder("[");
		boolean first = true;
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			InventoryItem item = inventory.getItem(slot);
			if (item == null) continue;
			if (!first) out.append(", ");
			first = false;
			out.append(slot).append(':').append(item.item.getStringID()).append('x').append(item.getAmount());
		}
		return out.append(']').toString();
	}
}
