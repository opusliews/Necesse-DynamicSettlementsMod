package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.Inventory;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.SettlementFoodAvailabilityCache;

@ModMethodPatch(target = Inventory.class, name = "markFullDirty", arguments = {})
public class SettlementFoodCacheInventoryFullDirtyPatch {
    @Advice.OnMethodExit
    public static void onExit(@Advice.This Inventory inventory) {
        SettlementFoodAvailabilityCache.onInventoryFullyChanged(inventory);
    }
}
