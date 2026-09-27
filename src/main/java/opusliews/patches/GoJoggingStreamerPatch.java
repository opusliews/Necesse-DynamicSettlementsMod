package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.level.maps.levelData.jobs.GoJoggingTileLevelJob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementJoggingSystem;

@ModMethodPatch(target = GoJoggingTileLevelJob.class, name = "getJobStreamer", arguments = {})
public class GoJoggingStreamerPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return(readOnly = false) JobTypeHandler.JobStreamSupplier result) {
		result = SettlementJoggingSystem.getJobStreamer();
	}
}
