package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementBedLevelIndicatorSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "moveSettler", arguments = {int.class, int.class, int.class, ServerClient.class})
public class ServerSettlementBedLevelSyncPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ServerSettlementData settlement,
			@Advice.Argument(3) ServerClient client,
			@Advice.Return boolean result) {
		if (!result || settlement == null || client == null) return;
		client.sendPacket(SettlementBedLevelIndicatorSystem.getSyncPacket(settlement));
	}
}
