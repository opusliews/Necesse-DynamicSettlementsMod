package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "onDisbanded", arguments = {})
public class SettlementMultiLevelDisbandPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ServerSettlementData settlement) {
		SettlementMultiLevelSystem.remove(settlement);
	}
}
