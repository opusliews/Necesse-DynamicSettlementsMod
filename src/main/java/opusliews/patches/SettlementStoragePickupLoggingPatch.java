package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import necesse.level.maps.levelData.settlementData.SettlementStoragePickupSlot;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.stock.SettlementStockSystem;

@ModMethodPatch(
		target = SettlementStoragePickupSlot.class,
		name = "pickupItem",
		arguments = {}
)
public class SettlementStoragePickupLoggingPatch {
	@Advice.OnMethodEnter
	public static int onEnter(@Advice.This SettlementStoragePickupSlot slot) {
		if (!Logging.logEnabled || slot == null || slot.storage == null || slot.item == null) return -1;
		InventoryRange range = slot.storage.getInventoryRange();
		if (range == null) return -1;
		return countItem(range, slot.item.item.getID());
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementStoragePickupSlot slot,
			@Advice.Enter int physicalBefore,
			@Advice.Return InventoryItem pickedUp
	) {
		if (!Logging.logEnabled || pickedUp == null || slot == null || slot.storage == null) return;

		int physicalAfter = -1;
		InventoryRange range = slot.storage.getInventoryRange();
		if (range != null) physicalAfter = countItem(range, pickedUp.item.getID());

		int stock = 0;
		if (slot.storage instanceof SettlementInventory) {
			stock = SettlementStockSystem.getStockTarget((SettlementInventory)slot.storage, pickedUp.item.getID());
		}

		Logging.logMessage(
				"[StorageTransfer] PICKUP chest=" + slot.storage.tileX + "," + slot.storage.tileY
						+ " slot=" + slot.slot
						+ " item=" + pickedUp.item.getStringID()
						+ " amount=" + pickedUp.getAmount()
						+ " physicalBefore=" + physicalBefore
						+ " physicalAfter=" + physicalAfter
						+ " stock=" + stock
						+ " belowStockAfter=" + (stock > 0 && physicalAfter >= 0 && physicalAfter < stock)
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
