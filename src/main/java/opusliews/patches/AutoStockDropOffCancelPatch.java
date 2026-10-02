package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.job.activeJob.DropOffSettlementStorageActiveJob;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.CraftingAutoStockSystem;

@ModMethodPatch(
		target = DropOffSettlementStorageActiveJob.class,
		name = "onCancelled",
		arguments = {boolean.class, boolean.class, boolean.class}
)
public class AutoStockDropOffCancelPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This DropOffSettlementStorageActiveJob job) {
		CraftingAutoStockSystem.cancelTrackedDropOff(job);
	}
}
