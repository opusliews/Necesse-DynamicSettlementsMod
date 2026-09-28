package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.jobs.CrossLevelHaulingSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "tickJobs", arguments = {})
public class SettlementCrossLevelHaulingTickPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ServerSettlementData settlement) {
		CrossLevelHaulingSystem.refresh(settlement);
	}
}
