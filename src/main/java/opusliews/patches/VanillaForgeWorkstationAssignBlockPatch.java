package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementStorageManager;
import net.bytebuddy.asm.Advice;
import opusliews.forge.VanillaForgeWorkstationCleanup;
import opusliews.logging.Logging;

@ModMethodPatch(target = SettlementStorageManager.class, name = "assignWorkstation", arguments = {int.class, int.class, boolean.class})
public class VanillaForgeWorkstationAssignBlockPatch {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(@Advice.This SettlementStorageManager manager,
                                  @Advice.Argument(0) int tileX, @Advice.Argument(1) int tileY) {
        if (manager == null || manager.data == null) return false;
        boolean blocked = VanillaForgeWorkstationCleanup.isForge(manager.data.getLevel(), tileX, tileY);
        if (blocked && Logging.logEnabled) Logging.logMessage("[CraftingForgeJob] Rejected vanilla forge workstation assignment tile=" + tileX + "," + tileY);
        return blocked;
    }
}
