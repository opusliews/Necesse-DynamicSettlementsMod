package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelPreferenceSystem;

@ModMethodPatch(target = HumanMob.class, name = "init", arguments = {})
public class HumanMobLevelPreferenceGenerationPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human) {
		SettlementLevelPreferenceSystem.initializeGeneratedPreference(human);
	}
}
