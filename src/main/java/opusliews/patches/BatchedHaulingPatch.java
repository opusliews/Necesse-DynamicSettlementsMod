package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.level.maps.levelData.jobs.HaulFromLevelJob;
import net.bytebuddy.asm.Advice;
import opusliews.jobs.BatchedHaulingSystem;

@ModMethodPatch(
		target = HaulFromLevelJob.class,
		name = "getJobSequence",
		arguments = {EntityJobWorker.class, JobTypeHandler.TypePriority.class}
)
public class BatchedHaulingPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static JobSequence onEnter(
			@Advice.This HaulFromLevelJob job,
			@Advice.Argument(0) EntityJobWorker worker,
			@Advice.Argument(1) JobTypeHandler.TypePriority priority
	) {
		return BatchedHaulingSystem.getBatchedJobSequence(job, worker, priority);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter JobSequence batched,
			@Advice.Return(readOnly = false) JobSequence result
	) {
		if (batched != null) result = batched;
	}
}
