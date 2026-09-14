package opusliews.patches;

import java.util.function.Consumer;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.packet.PacketDisconnect;
import necesse.engine.network.server.Server;
import net.bytebuddy.asm.Advice;
import opusliews.sleep.SleepSimulationSystem;

@ModMethodPatch(
		target = Server.class,
		name = "stop",
		arguments = {PacketDisconnect.class, Consumer.class}
)
public class SleepSimulationServerStopPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This Server server) {
		SleepSimulationSystem.stopServer(server);
	}
}
