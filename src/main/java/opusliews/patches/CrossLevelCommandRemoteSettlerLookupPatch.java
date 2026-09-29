package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.world.worldData.SettlersWorldData;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = SettlersWorldData.class, name = "getSettler", arguments = {int.class})
public class CrossLevelCommandRemoteSettlerLookupPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) int uniqueID, @Advice.Return(readOnly = false) HumanMob result) {
		if (result == null) result = SettlementCrossLevelCommandSystem.resolveCommandSettler(uniqueID);
	}
}
