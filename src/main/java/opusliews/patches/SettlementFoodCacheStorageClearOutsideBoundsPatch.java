package opusliews.patches;

import java.awt.Rectangle;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementStorageManager;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.SettlementFoodAvailabilityCache;

@ModMethodPatch(target = SettlementStorageManager.class, name = "clearOutsideBounds", arguments = {Rectangle.class})
public class SettlementFoodCacheStorageClearOutsideBoundsPatch {
    @Advice.OnMethodEnter
    public static int onEnter(@Advice.This SettlementStorageManager manager) {
        return manager == null || manager.getStorage() == null ? -1 : manager.getStorage().size();
    }

    @Advice.OnMethodExit
    public static void onExit(@Advice.This SettlementStorageManager manager, @Advice.Enter int beforeSize) {
        if (manager == null || manager.data == null || beforeSize < 0 || manager.getStorage() == null) return;
        if (manager.getStorage().size() != beforeSize) {
            SettlementFoodAvailabilityCache.invalidateTopology(manager.data, "surface-storage-outside-bounds-removed");
        }
    }
}
