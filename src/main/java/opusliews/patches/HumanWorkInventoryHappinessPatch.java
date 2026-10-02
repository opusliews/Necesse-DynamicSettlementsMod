package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.WorkInventory;
import net.bytebuddy.asm.Advice;
import opusliews.mobs.BuilderHumanMob;
import opusliews.settler.HappinessScaledWorkInventory;

@ModMethodPatch(target = HumanMob.class, name = "getWorkInventory", arguments = {})
public class HumanWorkInventoryHappinessPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This HumanMob human,
			@Advice.Return(readOnly = false) WorkInventory result
	) {
		if (result == null || !human.isSettler() || human instanceof BuilderHumanMob) return;
		result = new HappinessScaledWorkInventory(human, result);
	}
}
