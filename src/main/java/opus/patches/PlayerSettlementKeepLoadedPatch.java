package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = ServerSettlementData.class, name = "ensureRegionsLoaded", arguments = {boolean.class})
public class PlayerSettlementKeepLoadedPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This ServerSettlementData settlement,
			@Advice.Argument(value = 0, readOnly = false) boolean forceLoad
	) {
		if (settlement.networkData.hasOwner()) {
			forceLoad = true;
		}
	}
}
