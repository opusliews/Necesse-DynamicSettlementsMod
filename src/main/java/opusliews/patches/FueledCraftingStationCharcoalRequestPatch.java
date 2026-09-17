package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.gameObject.container.FueledCraftingStationObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.SettlementRequestOptions;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageItemIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecordsRegionData;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.CharcoalFuelSystem;

@ModMethodPatch(
		target = FueledCraftingStationObject.class,
		name = "getFuelRequestOptions",
		arguments = {Level.class, int.class, int.class}
)
public class FueledCraftingStationCharcoalRequestPatch {
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
