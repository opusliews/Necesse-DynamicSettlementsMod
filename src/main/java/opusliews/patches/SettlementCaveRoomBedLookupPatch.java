package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "addOrValidateBed", arguments = {int.class, int.class, boolean.class})
public class SettlementCaveRoomBedLookupPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This ServerSettlementData settlement,
			@Advice.Argument(0) int tileX,
			@Advice.Argument(1) int tileY,
			@Advice.Argument(2) boolean addOnlyPlayerPlaced,
			@Advice.Local("caveBed") SettlementBed caveBed) {
		if (!SettlementCaveBedSystem.hasCaveRoomContext(settlement)) return false;
		caveBed = SettlementCaveBedSystem.addOrValidateBedForCurrentRoomContext(settlement, tileX, tileY, addOnlyPlayerPlaced);
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Local("caveBed") SettlementBed caveBed,
			@Advice.Return(readOnly = false) SettlementBed result) {
		if (caveBed != null) result = caveBed;
	}
}
