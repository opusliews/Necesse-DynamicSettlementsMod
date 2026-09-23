package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameLinkedList;
import necesse.engine.world.WorldEntity;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import necesse.level.maps.levelData.settlementData.SettlementStoragePickupSlot;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.stock.SettlementStockSystem;

@ModMethodPatch(
		target = SettlementStoragePickupSlot.class,
		name = "init",
		arguments = {GameLinkedList.Element.class, WorldEntity.class}
)
public class SettlementStoragePickupReservationLoggingPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementStoragePickupSlot slot) {
		if (!Logging.logEnabled || slot == null || slot.storage == null || slot.item == null) return;

		int physical = -1;
		InventoryRange range = slot.storage.getInventoryRange();
		if (range != null) physical = countItem(range, slot.item.item.getID());

		int stock = 0;
		if (slot.storage instanceof SettlementInventory) {
			stock = SettlementStockSystem.getStockTarget((SettlementInventory)slot.storage, slot.item.item.getID());
		}

		Logging.logMessage(
				"[StorageReservation] PICKUP_CREATED chest=" + slot.storage.tileX + "," + slot.storage.tileY
						+ " slot=" + slot.slot
						+ " item=" + slot.item.item.getStringID()
						+ " amount=" + slot.item.getAmount()
						+ " physical=" + physical
						+ " stock=" + stock
		);
	}

	public static int countItem(InventoryRange range, int itemID) {
		int total = 0;
		for (int slot = range.startSlot; slot <= range.endSlot; slot++) {
			InventoryItem item = range.inventory.getItem(slot);
			if (item != null && item.item.getID() == itemID) total += item.getAmount();
		}
		return total;
	}
}
