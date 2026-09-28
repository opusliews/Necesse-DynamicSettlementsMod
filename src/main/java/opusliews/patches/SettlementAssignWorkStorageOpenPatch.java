package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementStorageAssignUI;

@ModMethodPatch(target = SettlementAssignWorkForm.class, name = "openStorageConfig", arguments = {int.class, int.class})
public class SettlementAssignWorkStorageOpenPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This SettlementAssignWorkForm form, @Advice.Argument(0) int tileX, @Advice.Argument(1) int tileY) {
		return SettlementStorageAssignUI.openStorageConfig(form, tileX, tileY);
	}
}
