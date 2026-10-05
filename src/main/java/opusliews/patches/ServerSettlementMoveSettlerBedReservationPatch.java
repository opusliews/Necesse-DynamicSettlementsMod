package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlerBedReservationSystem;
import opusliews.settlement.SettlementPlayerBedSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "moveSettler", arguments = {LevelSettler.class, SettlementBed.class, ServerClient.class})
public class ServerSettlementMoveSettlerBedReservationPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This ServerSettlementData settlement,
			@Advice.Argument(1) SettlementBed bed,
			@Advice.Argument(2) ServerClient client,
			@Advice.Local("releasedPlayerBed") SettlementPlayerBedSystem.PlayerBedAssignment releasedPlayerBed) {
		releasedPlayerBed = SettlementPlayerBedSystem.beginManualSettlerBedAssignment(settlement, bed, client);
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.This ServerSettlementData settlement,
			@Advice.Argument(0) LevelSettler settler,
			@Advice.Argument(1) SettlementBed bed,
			@Advice.Argument(2) ServerClient client,
			@Advice.Local("releasedPlayerBed") SettlementPlayerBedSystem.PlayerBedAssignment releasedPlayerBed,
			@Advice.Return boolean result) {
		SettlerBedReservationSystem.onBedAssigned(settler, bed, result);
		SettlementPlayerBedSystem.finishManualSettlerBedAssignment(settlement, client, releasedPlayerBed, result);
	}
}
