package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = HumanMob.class, name = "commandGuard", arguments = {ServerClient.class, int.class, int.class})
public class CrossLevelCommandGuardPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This HumanMob human, @Advice.Argument(0) ServerClient commander, @Advice.Argument(1) int x, @Advice.Argument(2) int y) {
		return SettlementCrossLevelCommandSystem.commandGuardCrossLevel(human, commander, x, y);
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human, @Advice.Enter boolean handled) {
		if (!handled) SettlementCrossLevelCommandSystem.onVanillaGuardCommand(human);
	}
}
