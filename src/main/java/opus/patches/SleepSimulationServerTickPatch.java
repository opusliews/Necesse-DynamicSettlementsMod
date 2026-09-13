package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.Server;
import net.bytebuddy.asm.Advice;
import opus.sleep.SleepSimulationSystem;

@ModMethodPatch(target = Server.class, name = "tick", arguments = {})
public class SleepSimulationServerTickPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This Server server) {
		SleepSimulationSystem.serverTick(server);
	}
}
