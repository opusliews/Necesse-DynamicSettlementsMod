package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.clayfiring.ClayFiringUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "setupButtons", arguments = {FormFlow.class, FormContentBox.class})
public class SettlementAssignWorkClayFiringPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementAssignWorkForm form,
			@Advice.Argument(0) FormFlow flow,
			@Advice.Argument(1) FormContentBox content) {
		ClayFiringUI.addAssignButton(form, flow, content);
	}
}
