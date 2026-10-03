package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.container.settlement.data.SettlementRestrictZoneData;
import necesse.inventory.container.settlement.events.SettlementRestrictZonesFullEvent;
import necesse.level.maps.levelData.settlementData.RestrictZone;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementIndependentZoneSystem;

@ModMethodPatch(target = SettlementRestrictZonesFullEvent.class, name = "<init>", arguments = {ServerSettlementData.class})
public class SettlementRestrictZonesFullIndependentPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementRestrictZonesFullEvent event, @Advice.Argument(0) ServerSettlementData data) {
		for (RestrictZone zone : SettlementIndependentZoneSystem.getAllCustomRestrictZones(data)) {
			event.zones.put(zone.uniqueID, new SettlementRestrictZoneData(zone));
		}
	}
}
