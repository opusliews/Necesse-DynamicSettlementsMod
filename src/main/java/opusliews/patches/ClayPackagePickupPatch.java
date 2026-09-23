package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob;
import net.bytebuddy.asm.Advice;
import opusliews.clay.ClayPackageSystem;

@ModMethodPatch(
		target = PickupSettlementStorageActiveJob.class,
		name = "perform",
		arguments = {}
)
public class ClayPackagePickupPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static ActiveJobResult onEnter(
			@Advice.This PickupSettlementStorageActiveJob job,
			@Advice.FieldValue(value = "worker") EntityJobWorker worker
	) {
		return ClayPackageSystem.tryPickupPackageItem(job, worker);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter ActiveJobResult packageResult,
			@Advice.Return(readOnly = false) ActiveJobResult result
	) {
		if (packageResult != null) result = packageResult;
	}
}
