package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.forge.VanillaForgeWorkstationCleanup;

@ModMethodPatch(target = ServerSettlementData.class, name = "serverTick", arguments = {})
public class VanillaForgeWorkstationCleanupTickPatch {
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.This ServerSettlementData settlement) {
        VanillaForgeWorkstationCleanup.tick(settlement);
    }
}
