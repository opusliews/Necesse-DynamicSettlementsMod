package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCaveBed;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "moveSettler", arguments = {LevelSettler.class, SettlementBed.class, ServerClient.class})
public class ServerSettlementMoveToCaveBedPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This ServerSettlementData settlement,
			@Advice.Argument(0) LevelSettler settler,
			@Advice.Argument(1) SettlementBed bed,
			@Advice.Local("handledResult") boolean handledResult) {
		if (!(bed instanceof SettlementCaveBed)) return false;
		if (settler == null || settler.data != settlement) throw new IllegalArgumentException("Settler settlement did not match this");

		if (!settlement.settlers.containsKey(settler.mobUniqueID)) settlement.settlers.put(settler);
		handledResult = SettlementCaveBedSystem.assignCaveBed(settler, (SettlementCaveBed)bed, true);
		if (!handledResult) return true;

		SettlerMob mob = settler.getMob();
		if (mob != null) {
			mob.assignBed(settler, bed, true);
			mob.makeSettler(settlement, settler);
			settler.settler.onMoveIn(settler);
		}
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(1) SettlementBed bed,
			@Advice.Local("handledResult") boolean handledResult,
			@Advice.Return(readOnly = false) boolean result) {
		if (bed instanceof SettlementCaveBed) result = handledResult;
	}
}
