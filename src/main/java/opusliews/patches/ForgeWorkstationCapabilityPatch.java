package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.container.settlement.SettlementContainerObjectStatusManager;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.engine.network.PacketReader;
import necesse.level.gameObject.ProcessingForgeObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

/**
 * Disable vanilla settlement workstation controls on processing forges at their
 * source: the container capability bit. This leaves settlement storage buttons
 * and Dynamic Settlements task-board automation untouched.
 */
@ModMethodPatch(target = SettlementContainerObjectStatusManager.class, name = "<init>",
        arguments = {SettlementDependantContainer.class, Level.class, int.class, int.class, PacketReader.class})
public class ForgeWorkstationCapabilityPatch {
    @Advice.OnMethodExit
    public static void onExit(
            @Advice.Argument(1) Level level,
            @Advice.FieldValue("masterTileX") int masterTileX,
            @Advice.FieldValue("masterTileY") int masterTileY,
            @Advice.FieldValue(value = "canSettlementWorkstationConfigure", readOnly = false) boolean canConfigure) {
        try {
            if (canConfigure && level != null
                    && level.getObject(masterTileX, masterTileY) instanceof ProcessingForgeObject) {
                canConfigure = false;
                if (Logging.logEnabled) {
                    Logging.logMessage("[CraftingForgeJob] Suppressed vanilla forge workstation configuration at "
                            + masterTileX + "," + masterTileY);
                }
            }
        } catch (Exception error) {
            Logging.logMessage("[CraftingForgeJob] Could not determine forge workstation capability: " + error);
        }
    }
}
