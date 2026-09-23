package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.FormSwitcherTyped;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementStorageConfigForm;
import net.bytebuddy.asm.Advice;
import opusliews.stock.SettlementStockUI;

// SettlementStorageConfigForm inherits dispose() from FormSwitcherTyped.
@ModMethodPatch(target = FormSwitcherTyped.class, name = "dispose", arguments = {})
public class SettlementStorageConfigFormDisposePatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This FormSwitcherTyped form) {
		if (form instanceof SettlementStorageConfigForm) {
			SettlementStockUI.detach((SettlementStorageConfigForm)form);
		}
	}
}
