package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementResidentSystem;

@ModMethodPatch(target = SettlerMob.class, name = "isSettlerWithinSettlementLoadedRegions", arguments = {NetworkSettlementData.class})
public class SettlerMultiLevelWithinLoadedRegionsPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlerMob settler,
			@Advice.Argument(0) NetworkSettlementData settlement,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (!result && SettlementResidentSystem.isSettlerInLoadedSettlementDomain(settler, settlement)) result = true;
	}
}
