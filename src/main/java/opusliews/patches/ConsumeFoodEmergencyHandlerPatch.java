package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.level.maps.levelData.jobs.ConsumeFoodLevelJob;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.EmergencyFoodCanPerform;
import opusliews.logging.Logging;

@ModMethodPatch(
		target = ConsumeFoodLevelJob.class,
		name = "handler",
		arguments = {EntityJobWorker.class, JobTypeHandler.class}
)
public class ConsumeFoodEmergencyHandlerPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) EntityJobWorker worker,
			@Advice.Return JobTypeHandler.SubHandler result
	) {
		if (result == null || !(worker instanceof HumanMob)) return;

		HumanMob human = (HumanMob)worker;
		result.setPredicate(new EmergencyFoodCanPerform(human));
		if (Logging.logEnabled && human.isServer()) {
			Logging.logMessage("[EmergencyFood] Extended ConsumeFood eligibility for settler="
					+ human.getStringID() + "#" + human.getUniqueID());
		}
	}
}
