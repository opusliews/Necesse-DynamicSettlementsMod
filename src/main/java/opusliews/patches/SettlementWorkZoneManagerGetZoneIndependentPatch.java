package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementWorkZoneManager;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.zones.SettlementIndependentZoneSystem;
import opusliews.zones.LevelScopedWorkZoneManager;

@ModMethodPatch(target = SettlementWorkZoneManager.class, name = "getZone", arguments = {int.class})
public class SettlementWorkZoneManagerGetZoneIndependentPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementWorkZoneManager manager, @Advice.Argument(0) int uniqueID, @Advice.Return(readOnly = false) SettlementWorkZone result) {
		if (result != null || manager == null || manager.data == null || manager instanceof LevelScopedWorkZoneManager) return;
		SettlementWorkZone custom = SettlementIndependentZoneSystem.getCustomWorkZone(manager.data, uniqueID);
		if (custom != null) {
			result = custom;
			if (Logging.logEnabled) Logging.logMessage("[IndependentZonesDebug] Surface manager getZone fallback resolved custom zone settlement="
					+ manager.data.uniqueID + " uniqueID=" + uniqueID
					+ " owner=" + SettlementIndependentZoneSystem.getWorkZoneLevel(manager.data, uniqueID)
					+ " type=" + custom.getStringID() + " size=" + custom.size());
		}
	}
}
