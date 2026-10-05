package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementContainerForm;
import net.bytebuddy.asm.Advice;
import opusliews.settlement.PlayerSettlementBedHUD;

@ModMethodPatch(target = SettlementContainerForm.class, name = "init", arguments = {})
public class SettlementPlayerBedHUDPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementContainerForm form) {
		if (form == null || form.dataManager == null || form.dataManager.client == null || form.dataManager.container == null) return;
		PlayerSettlementBedHUD.attach(form, form.dataManager.client, form.dataManager.container.getSettlementUniqueID());
	}
}
