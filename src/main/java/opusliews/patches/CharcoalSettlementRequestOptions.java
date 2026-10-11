package opusliews.patches;

import necesse.level.maps.levelData.settlementData.SettlementRequestOptions;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageItemIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecordsRegionData;
import opusliews.earlygame.CharcoalFuelSystem;

/** Public named class so vanilla methods containing inlined ByteBuddy advice can instantiate it. */
public final class CharcoalSettlementRequestOptions extends SettlementRequestOptions {
	public CharcoalSettlementRequestOptions() {
		super(5, 10);
	}

	@Override
	public SettlementStorageRecordsRegionData getRequestStorageData(SettlementStorageRecords records) {
		return ((SettlementStorageItemIDIndex)records.getIndex(SettlementStorageItemIDIndex.class))
				.getItem(CharcoalFuelSystem.charcoalStringID);
	}
}
