package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelPreferenceAIController;

@ModMethodPatch(target = HumanMob.class, name = "serverTick", arguments = {})
public class HumanMobLevelPreferenceWakePatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This HumanMob human) {
		SettlementLevelPreferenceAIController.wakePreferredLevelNode(human);
	}
}
