package opusliews.zones;

import necesse.level.maps.levelData.settlementData.SettlementWorkZoneManager;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;

public final class SettlementWorkZonePersistenceSupport {
	private SettlementWorkZonePersistenceSupport() {
	}

	public static void persistAndBroadcast(SettlementWorkZone zone, SettlementWorkZoneManager manager) {
		if (zone == null || !(manager instanceof LevelScopedWorkZoneManager)) return;
		LevelScopedWorkZoneManager levelManager = (LevelScopedWorkZoneManager)manager;
		levelManager.persist(zone);
		SettlementIndependentZoneActionSupport.broadcastWork(levelManager.data);
	}
}
