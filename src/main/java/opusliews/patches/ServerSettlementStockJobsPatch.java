package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.stock.SettlementStockSystem;

@ModMethodPatch(
		target = ServerSettlementData.class,
		name = "tickJobs",
		arguments = {}
)
public class ServerSettlementStockJobsPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ServerSettlementData settlement) {
		SettlementStockSystem.tickStockJobs(settlement);
	}
}
