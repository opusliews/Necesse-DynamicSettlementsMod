package opusliews.patches;

import java.util.function.Function;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.LevelIdentifier;
import net.bytebuddy.asm.Advice;
import opusliews.settlement.SettlementChestProtectionSystem;

@ModMethodPatch(target = ServerClient.class, name = "changeLevelCheck", arguments = {LevelIdentifier.class, Function.class, Function.class, boolean.class})
public class SettlementChestProtectionLevelSyncPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ServerClient client) {
		SettlementChestProtectionSystem.syncRelevantSettlements(client);
	}
}
