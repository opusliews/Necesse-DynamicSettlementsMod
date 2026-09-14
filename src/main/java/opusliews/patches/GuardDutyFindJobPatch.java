package opusliews.patches;

import java.util.function.Consumer;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.JobSequence;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardDutySystem;
import opusliews.guard.GuardNeedsSystem;

@ModMethodPatch(
		target = HumanMob.class,
		name = "findJob",
		arguments = {boolean.class, Consumer.class}
)
public class GuardDutyFindJobPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This HumanMob mob,
			@Advice.Argument(0) boolean ignoreRecreationJobs,
			@Advice.Argument(1) Consumer finderMod,
			@Advice.Local("guardDutyResult") JobSequence guardDutyResult
	) {
		if (!(mob instanceof GuardHumanMob)) {
			return false;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!GuardDutySystem.usesNightSchedule(guard)) {
			return false;
		}

		guardDutyResult = GuardNeedsSystem.findBreakJob(guard, ignoreRecreationJobs, finderMod);
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean handled,
			@Advice.Local("guardDutyResult") JobSequence guardDutyResult,
			@Advice.Return(readOnly = false) JobSequence result
	) {
		if (handled) {
			result = guardDutyResult;
		}
	}
}
