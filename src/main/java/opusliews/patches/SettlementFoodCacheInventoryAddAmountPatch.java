package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.Inventory;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.SettlementFoodAvailabilityCache;

@ModMethodPatch(target = Inventory.class, name = "addAmount", arguments = {int.class, int.class})
public class SettlementFoodCacheInventoryAddAmountPatch {
    @Advice.OnMethodExit
    public static void onExit(@Advice.This Inventory inventory, @Advice.Argument(0) int slot) {
        SettlementFoodAvailabilityCache.onInventorySlotChanged(inventory, slot);
    }
}
