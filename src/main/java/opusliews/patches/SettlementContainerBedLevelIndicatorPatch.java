package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.container.settlement.data.SettlementClientDataManager;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.network.PacketSettlementBedLevelRequest;

@ModMethodPatch(target = SettlementClientDataManager.class, name = "init", arguments = {})
public class SettlementContainerBedLevelIndicatorPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementClientDataManager manager) {
		if (manager == null || manager.client == null || manager.container == null) return;
		int settlementUniqueID = manager.container.getSettlementUniqueID();
		if (Logging.logEnabled) Logging.logMessage("[BedLevelIndicator] Requesting bed-level sync settlement=" + settlementUniqueID);
		manager.client.network.sendPacket(new PacketSettlementBedLevelRequest(settlementUniqueID));
	}
}
