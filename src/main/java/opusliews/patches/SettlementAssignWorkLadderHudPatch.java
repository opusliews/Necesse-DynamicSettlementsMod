package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLadderAssignUI;
import opusliews.multilevelsettlement.SettlementStorageAssignUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "updateHudElements", arguments = {})
public class SettlementAssignWorkLadderHudPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This SettlementAssignWorkForm form) {
		SettlementStorageAssignUI.beforeHudUpdate(form);
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementAssignWorkForm form) {
		SettlementLadderAssignUI.addHudElements(form);
	}
}
