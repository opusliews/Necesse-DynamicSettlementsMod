package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementResidentSystem;

@ModMethodPatch(target = LevelSettler.class, name = "getMob", arguments = {})
public class LevelSettlerMultiLevelMobLookupPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This LevelSettler levelSettler, @Advice.Return(readOnly = false) SettlerMob result) {
		SettlerMob liveDomainMob = SettlementResidentSystem.findLiveDomainMob(levelSettler);
		if (liveDomainMob != null) result = liveDomainMob;
	}
}
