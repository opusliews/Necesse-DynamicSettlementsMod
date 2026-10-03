package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZoneRegistry;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementWorkZoneClientUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "startAssignHusbandryZoneTool", arguments = {})
public class SettlementAssignWorkHusbandryIndependentPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This SettlementAssignWorkForm form) {
		return SettlementWorkZoneClientUI.startTool(form, SettlementWorkZoneRegistry.HUSBANDRY_ID);
	}
}
