package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementStorageManager;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.SettlementFoodAvailabilityCache;

@ModMethodPatch(target = SettlementStorageManager.class, name = "assignStorage", arguments = {int.class, int.class, boolean.class})
public class SettlementFoodCacheStorageAssignPatch {
    @Advice.OnMethodExit
    public static void onExit(@Advice.This SettlementStorageManager manager) {
        if (manager != null && manager.data != null) {
            SettlementFoodAvailabilityCache.invalidateTopology(manager.data, "surface-storage-assigned");
        }
    }
}
