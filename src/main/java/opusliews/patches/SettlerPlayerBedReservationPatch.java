package opusliews.patches;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import necesse.level.maps.levelData.settlementData.settler.Settler;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.settlement.SettlementPlayerBedSystem;

@ModMethodPatch(target = Settler.class, name = "canUseBed", arguments = {SettlementBed.class})
public class SettlerPlayerBedReservationPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) SettlementBed bed, @Advice.Return(readOnly = false) GameMessage result) {
		if (result != null || bed == null) return;
		if (!SettlementPlayerBedSystem.isPlayerReserved(bed)) return;
		result = new LocalMessage("misc", "playersettlementbedreserved");
		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Prevented settler bed use because bed is reserved for a player settlement=" + (bed.data == null ? "null" : bed.data.uniqueID) + " tile=" + bed.tileX + "," + bed.tileY);
	}
}
