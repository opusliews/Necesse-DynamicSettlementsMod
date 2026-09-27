package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.settler.SettlerThoughtsList;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;
import opusliews.multilevelsettlement.SettlementLevelPreferenceSystem;

@ModMethodPatch(target = HumanMob.class, name = "getStaticThoughts", arguments = {})
public class HumanMobStrandedThoughtPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human, @Advice.Return SettlerThoughtsList thoughts) {
		if (thoughts == null) return;
		String thoughtStringID = SettlementCaveBedSystem.getStrandedThoughtStringID(human);
		if (thoughtStringID != null) thoughts.addThought(thoughtStringID);
		String preferenceThoughtStringID = SettlementLevelPreferenceSystem.getHappinessThoughtStringID(human);
		if (preferenceThoughtStringID != null) thoughts.addThought(preferenceThoughtStringID);
	}
}
