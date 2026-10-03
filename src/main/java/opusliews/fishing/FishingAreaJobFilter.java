package opusliews.fishing;

import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobFinder;

import java.util.stream.Stream;

public final class FishingAreaJobFilter {
	private FishingAreaJobFilter() {
	}

	@SuppressWarnings("rawtypes")
	public static Stream filterFoundJobs(JobFinder finder, Stream stream) {
		if (finder == null || stream == null || !(finder.mob instanceof HumanMob)) return stream;
		HumanMob human = (HumanMob)finder.mob;
		return stream.filter(value -> !(value instanceof FoundJob) || FishingAreaSystem.isFishingJobAllowed(human, ((FoundJob)value).job));
	}
}
