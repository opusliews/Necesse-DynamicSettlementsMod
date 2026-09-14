package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import net.bytebuddy.asm.Advice;
import opusliews.DynamicSettlements;
import opusliews.crafting.IronAnvilFeature;

@ModMethodPatch(target = DynamicSettlements.class, name = "init", arguments = {})
public class IronAnvilRegistrationPatch {
	@Advice.OnMethodExit
	public static void onExit() {
		if (!DynamicSettlements.SBCompatFailure) {
			IronAnvilFeature.register();
		}
	}
}
