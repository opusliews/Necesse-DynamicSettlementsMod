package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementWorkZoneManager;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementIndependentZoneSystem;
import opusliews.zones.LevelScopedWorkZoneManager;

@ModMethodPatch(target = SettlementWorkZoneManager.class, name = "tickSecond", arguments = {})
public class SettlementWorkZoneManagerTickIndependentPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementWorkZoneManager manager) {
		if (manager == null || manager.data == null || manager instanceof LevelScopedWorkZoneManager) return;
		SettlementIndependentZoneSystem.tickSecond(manager.data);
	}
}
