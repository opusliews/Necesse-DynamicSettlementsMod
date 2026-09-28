package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementStorageAssignUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "startAssignStorageTool", arguments = {})
public class SettlementAssignWorkStorageToolPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This SettlementAssignWorkForm form) {
		return SettlementStorageAssignUI.startAssignTool(form);
	}
}
