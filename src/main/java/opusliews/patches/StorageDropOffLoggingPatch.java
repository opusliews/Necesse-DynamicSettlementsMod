package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import necesse.level.maps.levelData.settlementData.StorageDropOff;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.stock.SettlementStockSystem;

@ModMethodPatch(
		target = StorageDropOff.class,
		name = "addItem",
		arguments = {InventoryItem.class}
)
public class StorageDropOffLoggingPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This StorageDropOff dropOff,
			@Advice.Argument(0) InventoryItem item,
			@Advice.Local("physicalBefore") int physicalBefore,
			@Advice.Local("activeBefore") boolean activeBefore
	) {
		physicalBefore = -1;
		activeBefore = false;
		if (!Logging.logEnabled || item == null || dropOff == null || dropOff.storage == null) return;

		InventoryRange range = dropOff.storage.getInventoryRange();
		if (range != null) physicalBefore = countItem(range, item.item.getID());
		if (dropOff.storage instanceof SettlementInventory) {
			activeBefore = SettlementStockSystem.isRestocking((SettlementInventory)dropOff.storage, item.item.getID());
		}
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This StorageDropOff dropOff,
			@Advice.Argument(0) InventoryItem item,
			@Advice.Return int added,
			@Advice.Local("physicalBefore") int physicalBefore,
			@Advice.Local("activeBefore") boolean activeBefore
	) {
		if (!Logging.logEnabled || added <= 0 || item == null || dropOff == null || dropOff.storage == null) return;

		int physicalAfter = -1;
		int stock = 0;
		boolean activeAfter = false;
		InventoryRange range = dropOff.storage.getInventoryRange();
		if (range != null) physicalAfter = countItem(range, item.item.getID());
		if (dropOff.storage instanceof SettlementInventory) {
			SettlementInventory storage = (SettlementInventory)dropOff.storage;
			stock = SettlementStockSystem.getStockTarget(storage, item.item.getID());
			activeAfter = SettlementStockSystem.isRestocking(storage, item.item.getID());
		}

		Logging.logMessage(
				"[StorageTransfer] DEPOSIT chest=" + dropOff.storage.tileX + "," + dropOff.storage.tileY
						+ " item=" + item.item.getStringID()
						+ " amount=" + added
						+ " physicalBefore=" + physicalBefore
						+ " physicalAfter=" + physicalAfter
						+ " stock=" + stock
						+ " activeBefore=" + activeBefore
						+ " activeAfter=" + activeAfter
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
