package opusliews.patches;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import net.bytebuddy.asm.Advice;
import opusliews.sleep.SleepSimulationSystem;

@ModMethodPatch(target = Client.class, name = "frameTick", arguments = {TickManager.class})
public class SleepSimulationClientFrameTickPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This Client client) {
		SleepSimulationSystem.clientFrameTick(client);
	}
}
