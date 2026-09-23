package necesse.level.maps.levelData.settlementData;

import necesse.inventory.InventoryRange;

// Kept in the vanilla package to access the protected drop-off simulation without reflection.
public final class SettlementStockInventoryAccess {
	private SettlementStockInventoryAccess() {
	}

	public static InventoryRange copyFutureDropOffRange(LevelStorage storage) {
		StorageDropOffSimulation simulation = storage.dropOffSimulation;
		if (simulation.isDirty) simulation.update();
		InventoryRange range = simulation.simulatedRange;
		return range == null ? null : new InventoryRange(range.inventory.copy(), range.startSlot, range.endSlot);
	}
}
