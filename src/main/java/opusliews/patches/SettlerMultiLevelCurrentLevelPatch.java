package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementResidentSystem;

@ModMethodPatch(target = SettlerMob.class, name = "isSettlerOnCurrentLevel", arguments = {})
public class SettlerMultiLevelCurrentLevelPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlerMob settler, @Advice.Return(readOnly = false) boolean result) {
		if (!result && SettlementResidentSystem.isSettlerInSettlementDomain(settler)) result = true;
	}
}
