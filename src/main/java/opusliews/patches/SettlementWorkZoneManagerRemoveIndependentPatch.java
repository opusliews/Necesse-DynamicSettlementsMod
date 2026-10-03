package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementWorkZoneManager;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.zones.LevelScopedWorkZoneManager;
import opusliews.zones.SettlementIndependentZoneActionSupport;
import opusliews.zones.SettlementIndependentZoneSystem;

@ModMethodPatch(target = SettlementWorkZoneManager.class, name = "removeZone", arguments = {int.class})
public class SettlementWorkZoneManagerRemoveIndependentPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This SettlementWorkZoneManager manager,
			@Advice.Argument(0) int uniqueID,
			@Advice.Local("dsRemoved") boolean removed,
			@Advice.Local("dsSurface") boolean surface
	) {
		if (manager == null || manager.data == null || manager instanceof LevelScopedWorkZoneManager) return false;
		surface = manager.getZones().containsKey(uniqueID);
		if (surface) {
			if (Logging.logEnabled) Logging.logMessage("[IndependentZonesDebug] removeZone routed to vanilla surface manager settlement="
					+ manager.data.uniqueID + " uniqueID=" + uniqueID);
			return false;
		}
		if (SettlementIndependentZoneSystem.getCustomWorkZone(manager.data, uniqueID) == null) {
			if (Logging.logEnabled) Logging.logMessage("[IndependentZonesDebug] removeZone could not resolve zone settlement="
					+ manager.data.uniqueID + " uniqueID=" + uniqueID + " surfacePresent=false customPresent=false");
			return false;
		}
		if (Logging.logEnabled) Logging.logMessage("[IndependentZonesDebug] removeZone routed to custom manager settlement="
				+ manager.data.uniqueID + " uniqueID=" + uniqueID
				+ " owner=" + SettlementIndependentZoneSystem.getWorkZoneLevel(manager.data, uniqueID));
		removed = SettlementIndependentZoneSystem.deleteWorkZone(manager.data, uniqueID);
		if (removed) SettlementIndependentZoneActionSupport.broadcastWork(manager.data);
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementWorkZoneManager manager,
			@Advice.Argument(0) int uniqueID,
			@Advice.Enter boolean skipped,
			@Advice.Local("dsRemoved") boolean removed,
			@Advice.Local("dsSurface") boolean surface,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (skipped) result = removed;
		if (Logging.logEnabled && manager != null && manager.data != null && !(manager instanceof LevelScopedWorkZoneManager)) {
			Logging.logMessage("[IndependentZonesDebug] removeZone result settlement=" + manager.data.uniqueID
					+ " uniqueID=" + uniqueID + " route=" + (skipped ? "custom" : (surface ? "surface" : "vanilla-miss"))
					+ " result=" + result
					+ " surfaceNow=" + manager.getZones().containsKey(uniqueID)
					+ " customNow=" + (SettlementIndependentZoneSystem.getCustomWorkZone(manager.data, uniqueID) != null));
		}
	}
}
