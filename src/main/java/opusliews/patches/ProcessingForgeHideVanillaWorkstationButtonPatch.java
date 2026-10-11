package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementObjectStatusFormManager;
import necesse.inventory.container.settlement.SettlementContainerObjectStatusManager;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ProcessingForgeObject;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

/** Suppress the *vanilla workstation* button on the Forge, without touching storage or task boards. */
@ModMethodPatch(target = SettlementObjectStatusFormManager.class,
        name = "addWorkstationConfigButton", arguments = {Form.class, int.class, int.class})
public class ProcessingForgeHideVanillaWorkstationButtonPatch {
    @Advice.OnMethodExit
    public static void onExit(@Advice.This SettlementObjectStatusFormManager formManager,
                              @Advice.Argument(0) Form form,
                              @Advice.FieldValue("manager") SettlementContainerObjectStatusManager manager,
                              @Advice.Return(readOnly = false) boolean added) {
        if (hideForgeButton(formManager, form, manager, added)) added = false;
    }

    public static boolean hideForgeButton(SettlementObjectStatusFormManager formManager,
                                         Form form,
                                         SettlementContainerObjectStatusManager manager,
                                         boolean added) {
        if (manager == null || manager.level == null) return false;
        try {
            int x = manager.masterTileX;
            int y = manager.masterTileY;

            GameObject object = manager.level.getObject(x, y);

            boolean forge = object instanceof ProcessingForgeObject
                    || object != null && "forge".equals(object.getStringID());
            if (!forge) return false;

            if (formManager != null && formManager.configureWorkstationButton != null) {
                if (form != null) form.getComponentList().removeComponent(formManager.configureWorkstationButton);
                formManager.configureWorkstationButton = null;
            }
            if (Logging.logEnabled) {
                Logging.logMessage("[CraftingForgeJob] Suppressed vanilla Forge workstation button at "
                        + x + "," + y + " originalAdded=" + added
                        + " object=" + object.getStringID());
            }
            return true;
        } catch (Exception error) {
            Logging.logMessage("[CraftingForgeJob] Failed suppressing vanilla Forge workstation button: " + error);
            return false;
        }
    }
}
