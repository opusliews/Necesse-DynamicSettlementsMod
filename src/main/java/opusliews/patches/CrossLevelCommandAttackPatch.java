package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = HumanMob.class, name = "commandAttack", arguments = {ServerClient.class, Mob.class})
public class CrossLevelCommandAttackPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This HumanMob human, @Advice.Argument(0) ServerClient commander, @Advice.Argument(1) Mob target) {
		return SettlementCrossLevelCommandSystem.commandAttackCrossLevel(human, commander, target);
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human, @Advice.Enter boolean handled) {
		if (!handled) SettlementCrossLevelCommandSystem.onVanillaAttackCommand(human);
	}
}
