package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.inventory.container.mobCommands.CommandCustomAction;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = CommandCustomAction.class, name = "executePacket", arguments = {PacketReader.class})
public class CrossLevelCommandResolveContextPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This CommandCustomAction action) {
		if (action != null && action.handler != null && action.handler.container != null && action.handler.container.client.isServer()) {
			SettlementCrossLevelCommandSystem.beginCommandResolution(action.handler.container.client.getServerClient());
		}
	}

	@Advice.OnMethodExit
	public static void onExit() {
		SettlementCrossLevelCommandSystem.endCommandResolution();
	}
}
