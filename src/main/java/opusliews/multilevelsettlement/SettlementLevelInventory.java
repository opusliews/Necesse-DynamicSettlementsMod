package opusliews.multilevelsettlement;

import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;

public class SettlementLevelInventory extends SettlementInventory {
	private final ServerSettlementData settlement;

	public SettlementLevelInventory(ServerSettlementData settlement, Level level, int tileX, int tileY) {
		super(level, tileX, tileY);
		this.settlement = settlement;
	}

	@Override
	public boolean isStorageValid() {
		return SettlementLevelStorageManager.hasInventory(settlement, this);
	}
}
