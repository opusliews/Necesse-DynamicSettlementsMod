package opusliews.patches;

import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementContainerForm;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementWorkPrioritiesForm;
import necesse.inventory.container.settlement.SettlementContainer;
import necesse.engine.network.client.Client;
import net.bytebuddy.asm.Advice;
import opusliews.charcoal.CharcoalProductionPriorityUI;

@ModConstructorPatch(
		target = SettlementWorkPrioritiesForm.class,
		arguments = {Client.class, SettlementContainer.class, SettlementContainerForm.class}
)
public class SettlementWorkPrioritiesCharcoalProductionPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementWorkPrioritiesForm form) {
		CharcoalProductionPriorityUI.expandCharcoalColumn(form);
	}
}
