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
	public static boolean onEnter(
			@Advice.This HaulFromLevelJob job,
			@Advice.Argument(0) EntityJobWorker worker,
			@Advice.Argument(1) JobTypeHandler.TypePriority priority,
			@Advice.Local("batchedHaulingResult") JobSequence batchedHaulingResult
	) {
		batchedHaulingResult = BatchedHaulingSystem.getBatchedJobSequence(job, worker, priority);
		if (batchedHaulingResult != null) return true;
		return BatchedHaulingSystem.shouldPreventVanillaFallback(job);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean handled,
			@Advice.Local("batchedHaulingResult") JobSequence batchedHaulingResult,
			@Advice.Return(readOnly = false) JobSequence result
	) {
		if (handled) result = batchedHaulingResult;
	}
}
