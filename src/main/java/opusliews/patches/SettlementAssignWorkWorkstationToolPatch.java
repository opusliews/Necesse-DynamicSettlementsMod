package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementWorkstationAssignUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "startAssignWorkstationTool", arguments = {})
public class SettlementAssignWorkWorkstationToolPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This SettlementAssignWorkForm form) {
		return SettlementWorkstationAssignUI.startAssignTool(form);
	}
}
