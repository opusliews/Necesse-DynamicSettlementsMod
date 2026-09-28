package opusliews.multilevelsettlement;

import java.lang.reflect.Method;

import necesse.engine.util.GameLinkedList;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import necesse.level.maps.levelData.settlementData.SettlementStoragePickupSlot;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecord;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;

public class SettlementLevelInventory extends SettlementInventory {
	private static final Method dropOffUpdateMethod;

	static {
		try {
			dropOffUpdateMethod = necesse.level.maps.levelData.settlementData.StorageDropOffSimulation.class.getDeclaredMethod("update");
			dropOffUpdateMethod.setAccessible(true);
		}
		catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private final ServerSettlementData settlement;

	public SettlementLevelInventory(ServerSettlementData settlement, Level level, int tileX, int tileY) {
		super(level, tileX, tileY);
		this.settlement = settlement;
	}

	@Override
	public boolean isStorageValid() {
		return SettlementLevelStorageManager.hasInventory(settlement, this);
	}

	/**
	 * Mirrors the part of ServerSettlementData.tickJobs that keeps settlement storage
	 * reservation simulations and storage indexes current. The fields are protected on
	 * LevelStorage, so doing this in the level-aware subclass lets us preserve vanilla's
	 * reserved-item accounting without reflection.
	 */
	public void tickLevelStorage(SettlementStorageRecords records) {
		updateAdjacentSolidState();
		removeInvalidPickups();
		try {
			dropOffUpdateMethod.invoke(dropOffSimulation);
		}
		catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		if (records == null || isAllAdjacentSolid()) return;

		InventoryRange range = getInventoryRange();
		if (range == null) return;
		for (int slot = range.startSlot; slot <= range.endSlot; slot++) {
			InventoryItem item = range.inventory.getItem(slot);
			if (item == null || item.getAmount() <= 0) continue;
			GameLinkedList pickupList = (GameLinkedList)pickupSlots.get(slot);
			int reserved = pickupList.stream()
					.filter(value -> ((SettlementStoragePickupSlot)value).isReserved(level.getWorldEntity()))
					.mapToInt(value -> ((SettlementStoragePickupSlot)value).item.getAmount())
					.sum();
			int available = Math.max(0, item.getAmount() - reserved);
			if (available > 0) records.add(item, new SettlementStorageRecord(this, slot, item, available));
		}
	}
}
