package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.FirstNightDeathSkipSystem;

@ModMethodPatch(target = ServerClient.class, name = "respawn", arguments = {})
public class ServerClientFirstNightRespawnPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This ServerClient client) {
		FirstNightDeathSkipSystem.onServerRespawn(client);
	}
}
