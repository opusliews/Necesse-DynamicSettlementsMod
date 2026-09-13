package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import net.bytebuddy.asm.Advice;
import opus.sleep.SleepSimulationSystem;

@ModMethodPatch(target = Client.class, name = "disconnect", arguments = {String.class})
public class SleepSimulationClientDisconnectPatch {
	@Advice.OnMethodEnter
	public static void onEnter() {
		SleepSimulationSystem.stopClient();
	}
}
