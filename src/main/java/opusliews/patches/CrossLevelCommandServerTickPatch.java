package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = HumanMob.class, name = "serverTick", arguments = {})
public class CrossLevelCommandServerTickPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human) {
		SettlementCrossLevelCommandSystem.restoreFollowAfterServerTick(human);
	}
}
