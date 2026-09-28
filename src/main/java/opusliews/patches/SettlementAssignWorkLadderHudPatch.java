package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLadderAssignUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "updateHudElements", arguments = {})
public class SettlementAssignWorkLadderHudPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementAssignWorkForm form) {
		SettlementLadderAssignUI.addHudElements(form);
	}
}
