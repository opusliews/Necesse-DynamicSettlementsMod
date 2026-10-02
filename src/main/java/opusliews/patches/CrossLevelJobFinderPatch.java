package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobFinder;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelJobSystem;

@ModMethodPatch(target = JobFinder.class, name = "findJob", arguments = {boolean.class})
public class CrossLevelJobFinderPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This JobFinder finder,
			@Advice.Argument(0) boolean ignoreRecreationJobs,
			@Advice.Local("dsCrossLevelFoundJob") FoundJob foundJob
	) {
		foundJob = SettlementCrossLevelJobSystem.findPendingExactJob(finder, ignoreRecreationJobs);
		if (foundJob == null) foundJob = SettlementCrossLevelJobSystem.findRemoteRelocation(finder, ignoreRecreationJobs);
		return foundJob != null;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean handled,
			@Advice.Local("dsCrossLevelFoundJob") FoundJob foundJob,
			@Advice.Return(readOnly = false) FoundJob result
	) {
		if (handled) result = foundJob;
	}
}
