package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementWorkZoneClientUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "dispose", arguments = {})
public class SettlementAssignWorkDisposeStatePatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This SettlementAssignWorkForm form) {
		SettlementWorkZoneClientUI.unregisterForm(form);
	}
}
