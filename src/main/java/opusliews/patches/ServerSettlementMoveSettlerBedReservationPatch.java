package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlerBedReservationSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "moveSettler", arguments = {LevelSettler.class, SettlementBed.class, ServerClient.class})
public class ServerSettlementMoveSettlerBedReservationPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) LevelSettler settler,
			@Advice.Argument(1) SettlementBed bed,
			@Advice.Return boolean result) {
		SettlerBedReservationSystem.onBedAssigned(settler, bed, result);
	}
}
