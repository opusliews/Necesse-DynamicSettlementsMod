package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.DropOffSettlementStorageActiveJob;
import net.bytebuddy.asm.Advice;
import opusliews.clay.ClayPackageSystem;
import opusliews.crafting.CraftingAutoStockSystem;

@ModMethodPatch(
		target = DropOffSettlementStorageActiveJob.class,
		name = "perform",
		arguments = {}
)
public class ClayPackageDropOffPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static ActiveJobResult onEnter(
			@Advice.This DropOffSettlementStorageActiveJob job,
			@Advice.FieldValue(value = "worker") EntityJobWorker worker,
			@Advice.Local("autoStockBeforeCount") int autoStockBeforeCount
	) {
		autoStockBeforeCount = CraftingAutoStockSystem.captureDropOffItemCount(job);
		ActiveJobResult autoStockResult = CraftingAutoStockSystem.tryPerformTrackedDropOff(job, worker);
		if (autoStockResult != null) return autoStockResult;
		return ClayPackageSystem.tryDropOffPackageItem(job, worker);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This DropOffSettlementStorageActiveJob job,
			@Advice.Enter ActiveJobResult packageResult,
			@Advice.Local("autoStockBeforeCount") int autoStockBeforeCount,
			@Advice.Return(readOnly = false) ActiveJobResult result
	) {
		if (packageResult != null) result = packageResult;
		if (result != ActiveJobResult.PERFORMING) {
			CraftingAutoStockSystem.finishTrackedDropOff(job, result, autoStockBeforeCount);
		}
	}
}
