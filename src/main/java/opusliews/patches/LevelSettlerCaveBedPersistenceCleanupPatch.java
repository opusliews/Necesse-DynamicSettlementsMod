package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;

@ModMethodPatch(target = LevelSettler.class, name = "assignBed", arguments = {SettlementBed.class})
public class LevelSettlerCaveBedPersistenceCleanupPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This LevelSettler settler) {
		SettlementCaveBedSystem.onNonCaveBedAssigned(settler);
	}
}
