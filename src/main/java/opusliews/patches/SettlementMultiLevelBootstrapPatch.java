package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "ensureRegionsLoaded", arguments = {boolean.class})
public class SettlementMultiLevelBootstrapPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This ServerSettlementData settlement) {
		SettlementMultiLevelSystem.get(settlement);
	}
}
