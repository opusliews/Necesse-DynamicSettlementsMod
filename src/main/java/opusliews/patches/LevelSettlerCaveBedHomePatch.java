package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCaveBed;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;

@ModMethodPatch(target = LevelSettler.class, name = "updateHome", arguments = {})
public class LevelSettlerCaveBedHomePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This LevelSettler settler) {
		if (!(settler.getBed() instanceof SettlementCaveBed)) return false;
		SettlementCaveBedSystem.updateHome(settler);
		return true;
	}
}
