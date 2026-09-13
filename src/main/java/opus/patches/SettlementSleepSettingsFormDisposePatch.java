package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementSettingsForm;
import net.bytebuddy.asm.Advice;
import opus.forms.SettlementSleepSettingsFormSystem;

@ModMethodPatch(target = SettlementSettingsForm.class, name = "dispose", arguments = {})
public class SettlementSleepSettingsFormDisposePatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This SettlementSettingsForm form) {
		SettlementSleepSettingsFormSystem.onDisposed(form);
	}
}
