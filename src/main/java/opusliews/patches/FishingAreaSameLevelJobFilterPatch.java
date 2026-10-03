package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.job.JobFinder;
import net.bytebuddy.asm.Advice;
import opusliews.fishing.FishingAreaJobFilter;

import java.util.stream.Stream;

@ModMethodPatch(target = JobFinder.class, name = "streamFoundJobs", arguments = {boolean.class})
public class FishingAreaSameLevelJobFilterPatch {
	@Advice.OnMethodExit
	@SuppressWarnings("rawtypes")
	public static void onExit(
			@Advice.This JobFinder finder,
			@Advice.Return(readOnly = false) Stream result) {
		result = FishingAreaJobFilter.filterFoundJobs(finder, result);
	}
}
