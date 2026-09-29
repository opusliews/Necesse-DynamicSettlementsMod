package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = HumanMob.class, name = "clearCommandsOrders", arguments = {ServerClient.class})
public class CrossLevelCommandClearPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human) {
		SettlementCrossLevelCommandSystem.clear(human);
	}
}
