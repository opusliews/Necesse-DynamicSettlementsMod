package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementRestrictForm;
import net.bytebuddy.asm.Advice;
import opusliews.network.PacketSettlementIndependentZonesRequest;

@ModMethodPatch(target = SettlementRestrictForm.class, name = "init", arguments = {})
public class SettlementRestrictIndependentZonesSyncPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementRestrictForm form) {
		if (form != null && form.client != null && form.client.network != null) {
			form.client.network.sendPacket(new PacketSettlementIndependentZonesRequest(form.container.getSettlementUniqueID()));
		}
	}
}
