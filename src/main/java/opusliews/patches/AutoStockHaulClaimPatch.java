package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobSequence;
import necesse.level.maps.levelData.jobs.HaulFromLevelJob;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.CraftingAutoStockSystem;

@ModMethodPatch(
		target = HaulFromLevelJob.class,
		name = "getJobSequence",
		arguments = {EntityJobWorker.class, FoundJob.class}
)
public class AutoStockHaulClaimPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) EntityJobWorker worker,
			@Advice.Argument(1) FoundJob foundJob,
			@Advice.Return JobSequence result
	) {
		if (result == null || foundJob == null || !(foundJob.job instanceof HaulFromLevelJob)) return;
		CraftingAutoStockSystem.markHaulJobClaimed((HaulFromLevelJob)foundJob.job, worker);
	}
}
