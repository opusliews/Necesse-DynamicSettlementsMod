package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.network.PacketSettlementIndependentZonesRequest;
import opusliews.zones.SettlementWorkZoneClientUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "init", arguments = {})
public class SettlementAssignWorkIndependentZonesSyncPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementAssignWorkForm form) {
		SettlementWorkZoneClientUI.registerForm(form);
		if (form != null && form.client != null && form.client.network != null) {
			form.client.network.sendPacket(new PacketSettlementIndependentZonesRequest(form.container.getSettlementUniqueID()));
		}
	}
}
