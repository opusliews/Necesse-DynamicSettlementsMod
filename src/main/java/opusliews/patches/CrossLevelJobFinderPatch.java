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
		foundJob = SettlementCrossLevelJobSystem.findCrossLevelFoodRelocation(finder);
		if (foundJob != null) return true;

		// ConsumeFood has vanilla maximum first priority. Preserve that across levels: when
		// executable food exists locally, step aside immediately so vanilla can take it before
		// a remembered or newly-selected cross-level work job.
		if (SettlementCrossLevelJobSystem.shouldPreferLocalFood(finder)) return false;

		// At critical hunger, if neither local nor remote food can currently be executed, do not
		// let unrelated cross-level work slip in while the emergency state is still active.
		if (SettlementCrossLevelJobSystem.isEmergencyFoodSearch(finder)) return false;

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
