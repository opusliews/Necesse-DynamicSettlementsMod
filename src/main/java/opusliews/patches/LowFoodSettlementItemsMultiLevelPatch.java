package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.notifications.LowFoodSettlementNotification;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;

@ModMethodPatch(target = LowFoodSettlementNotification.class, name = "getTotalItems", arguments = {ServerSettlementData.class})
public class LowFoodSettlementItemsMultiLevelPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) ServerSettlementData settlement,
			@Advice.Return(readOnly = false) int result) {
		result = SettlementLevelStorageManager.getTotalFoodItems(settlement);
	}
}
