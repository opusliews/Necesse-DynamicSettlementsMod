package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementWorkZoneClientUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "onSetCurrent", arguments = {boolean.class})
public class SettlementAssignWorkActiveStatePatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This SettlementAssignWorkForm form,
			@Advice.Argument(0) boolean current
	) {
		SettlementWorkZoneClientUI.setFormActive(form, current);
	}
}
