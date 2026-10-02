package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.job.activeJob.DropOffSettlementStorageActiveJob;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.CraftingAutoStockSystem;

@ModMethodPatch(
		target = DropOffSettlementStorageActiveJob.class,
		name = "isValid",
		arguments = {boolean.class}
)
public class AutoStockDropOffValidityPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This DropOffSettlementStorageActiveJob job,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (CraftingAutoStockSystem.isTrackedDropOff(job)) {
			result = CraftingAutoStockSystem.isTrackedDropOffValid(job);
		}
	}
}
