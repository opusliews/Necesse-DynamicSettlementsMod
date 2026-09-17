package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.FueledIncineratorObjectEntity;
import necesse.level.maps.levelData.settlementData.SettlementRequestOptions;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageItemIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecordsRegionData;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.CharcoalFuelSystem;

@ModMethodPatch(
		target = FueledIncineratorObjectEntity.class,
		name = "getSettlementFuelRequestOptions",
		arguments = {}
)
public class IncineratorCharcoalRequestPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return(readOnly = false) SettlementRequestOptions result) {
		result = new SettlementRequestOptions(5, 10) {
			@Override
			public SettlementStorageRecordsRegionData getRequestStorageData(SettlementStorageRecords records) {
				return ((SettlementStorageItemIDIndex)records.getIndex(SettlementStorageItemIDIndex.class))
						.getItem(CharcoalFuelSystem.charcoalStringID);
			}
		};
	}
}
