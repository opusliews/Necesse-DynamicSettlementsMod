package opusliews.multilevelsettlement;

import necesse.engine.util.PointHashMap;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementRoom;

public class SettlementCaveRoom extends SettlementRoom {
	private final Level caveLevel;

	public SettlementCaveRoom(ServerSettlementData data, PointHashMap roomsMap, Level caveLevel, int tileX, int tileY) {
		super(data, roomsMap, tileX, tileY);
		this.caveLevel = caveLevel;
	}

	@Override
	public Level getLevel() {
		return caveLevel;
	}

	@Override
	protected void calculateStats() {
		SettlementCaveBedSystem.beginCaveRoomCalculation(data, caveLevel);
		try {
			super.calculateStats();
		} finally {
			SettlementCaveBedSystem.endCaveRoomCalculation();
		}
	}
}
