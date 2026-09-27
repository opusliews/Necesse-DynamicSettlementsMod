package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;

@ModMethodPatch(target = LevelSettler.class, name = "serverTick", arguments = {})
public class LevelSettlerCaveBedValidationPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This LevelSettler settler) {
		SettlementCaveBedSystem.validateAssignedCaveBed(settler);
	}
}
