package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import net.bytebuddy.asm.Advice;
import opusliews.stock.SettlementStockSystem;

@ModMethodPatch(
		target = SettlementInventory.class,
		name = "fromLoadData",
		arguments = {ServerSettlementData.class, LoadData.class, int.class, int.class}
)
public class SettlementInventoryStockLoadPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(1) LoadData save,
			@Advice.Return SettlementInventory storage
	) {
		if (storage != null) SettlementStockSystem.load(storage, save);
	}
}
