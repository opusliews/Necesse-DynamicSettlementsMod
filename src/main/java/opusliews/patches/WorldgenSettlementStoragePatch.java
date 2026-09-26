package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.InventoryRange;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenLockedContainerSystem;

@ModMethodPatch(target = SettlementInventory.class, name = "getInventoryRange", arguments = {})
public class WorldgenSettlementStoragePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementInventory inventory, @Advice.Return(readOnly = false) InventoryRange result) {
		if (WorldgenLockedContainerSystem.blocksSettlementStorage(inventory.level, inventory.tileX, inventory.tileY)) {
			result = null;
		}
	}
}
