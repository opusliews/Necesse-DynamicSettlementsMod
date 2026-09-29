package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementPersonalityLevelSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "getLevel", arguments = {})
public class ServerSettlementPersonalityLevelContextPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ServerSettlementData settlement, @Advice.Return(readOnly = false) Level result) {
		Level contextLevel = SettlementPersonalityLevelSystem.getContextLevel(settlement);
		if (contextLevel != null) result = contextLevel;
	}
}
