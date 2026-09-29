package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = HumanMob.class, name = "commandFollow", arguments = {ServerClient.class, Mob.class})
public class CrossLevelCommandFollowPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human, @Advice.Argument(1) Mob target) {
		SettlementCrossLevelCommandSystem.onFollowCommand(human, target);
	}
}
