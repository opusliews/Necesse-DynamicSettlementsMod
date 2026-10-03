package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementWorkZoneManager;
import net.bytebuddy.asm.Advice;
import opusliews.zones.LevelScopedWorkZoneManager;
import opusliews.zones.SettlementIndependentZoneSystem;

@ModMethodPatch(target = SettlementWorkZoneManager.class, name = "tickJobs", arguments = {})
public class SettlementWorkZoneManagerJobsIndependentPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementWorkZoneManager manager) {
		if (manager == null || manager.data == null || manager instanceof LevelScopedWorkZoneManager) return;
		SettlementIndependentZoneSystem.tickJobs(manager.data);
	}
}
