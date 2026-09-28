package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementWorkstationAssignUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "openWorkstationConfig", arguments = {int.class, int.class})
public class SettlementAssignWorkWorkstationOpenPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This SettlementAssignWorkForm form, @Advice.Argument(0) int tileX, @Advice.Argument(1) int tileY) {
		return SettlementWorkstationAssignUI.openWorkstationConfig(form, tileX, tileY);
	}
}
