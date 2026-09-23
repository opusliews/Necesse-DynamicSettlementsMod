package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.SaveData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import net.bytebuddy.asm.Advice;
import opusliews.stock.SettlementStockSystem;

@ModMethodPatch(
		target = SettlementInventory.class,
		name = "addSaveData",
		arguments = {SaveData.class}
)
public class SettlementInventoryStockSavePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementInventory storage,
			@Advice.Argument(0) SaveData save
	) {
		SettlementStockSystem.save(storage, save);
	}
}
