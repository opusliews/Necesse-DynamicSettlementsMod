package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.jobs.GoJoggingTileLevelJob;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = GoJoggingTileLevelJob.class, name = "isValid", arguments = {})
public class GoJoggingIsValidPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This GoJoggingTileLevelJob job, @Advice.Return(readOnly = false) boolean result) {
		if (!result && job.getLevel() != null && job.getLevel().isCave) result = !job.isRemoved();
	}
}
