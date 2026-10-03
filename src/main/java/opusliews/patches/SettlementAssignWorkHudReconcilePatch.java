package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementWorkZoneClientUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "updateHudElements", arguments = {})
public class SettlementAssignWorkHudReconcilePatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This SettlementAssignWorkForm form) {
		SettlementWorkZoneClientUI.reconcileCachedWorkZones(form);
	}
}
