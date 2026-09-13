package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementSettingsForm;
import net.bytebuddy.asm.Advice;
import opus.forms.SettlementSleepSettingsFormSystem;

@ModMethodPatch(target = SettlementSettingsForm.class, name = "update", arguments = {})
public class SettlementSleepSettingsFormPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementSettingsForm form,
			@Advice.FieldValue("settings") Form settings
	) {
		SettlementSleepSettingsFormSystem.onSettingsUpdated(form, settings);
	}
}
