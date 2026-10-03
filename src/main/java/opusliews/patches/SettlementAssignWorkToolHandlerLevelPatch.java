package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkToolHandler;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.zones.SettlementWorkZoneClientUI;
import opusliews.zones.SettlementZoneClientCache;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@ModMethodPatch(
		target = SettlementAssignWorkToolHandler.class,
		name = "addOptionsAndTooltips",
		arguments = {Point.class, Consumer.class, Consumer.class}
)
public class SettlementAssignWorkToolHandlerLevelPatch {
	@Advice.OnMethodEnter
	public static HashMap<Integer, SettlementWorkZone> onEnter(@Advice.This SettlementAssignWorkToolHandler handler) {
		return removeOtherLevelZones(handler);
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void onExit(
			@Advice.This SettlementAssignWorkToolHandler handler,
			@Advice.Enter HashMap<Integer, SettlementWorkZone> removed
	) {
		restoreOtherLevelZones(handler, removed);
	}

	public static HashMap<Integer, SettlementWorkZone> removeOtherLevelZones(SettlementAssignWorkToolHandler handler) {
		HashMap<Integer, SettlementWorkZone> removed = new HashMap<>();
		if (handler == null || handler.workForm == null || handler.container == null || handler.level == null) return removed;

		HashMap<Integer, SettlementWorkZone> zones = SettlementWorkZoneClientUI.getZones(handler.workForm);
		if (zones == null || zones.isEmpty()) return removed;

		int settlementUniqueID = handler.container.getSettlementUniqueID();
		for (Map.Entry<Integer, SettlementWorkZone> entry : new HashMap<>(zones).entrySet()) {
			if (!SettlementZoneClientCache.isWorkZoneOnLevel(
					settlementUniqueID,
					entry.getKey(),
					handler.level.getIdentifier()
			)) {
				removed.put(entry.getKey(), entry.getValue());
				zones.remove(entry.getKey());
			}
		}

		if (Logging.logEnabled && !removed.isEmpty()) {
			Logging.logMessage("[IndependentZonesDebug] Filtered " + removed.size()
					+ " other-level work zones from Assign Work hover/config lookup settlement=" + settlementUniqueID
					+ " level=" + handler.level.getIdentifier());
		}
		return removed;
	}

	public static void restoreOtherLevelZones(
			SettlementAssignWorkToolHandler handler,
			HashMap<Integer, SettlementWorkZone> removed
	) {
		if (handler == null || handler.workForm == null || removed == null || removed.isEmpty()) return;
		HashMap<Integer, SettlementWorkZone> zones = SettlementWorkZoneClientUI.getZones(handler.workForm);
		if (zones == null) return;
		for (Map.Entry<Integer, SettlementWorkZone> entry : removed.entrySet()) {
			zones.putIfAbsent(entry.getKey(), entry.getValue());
		}
	}
}
