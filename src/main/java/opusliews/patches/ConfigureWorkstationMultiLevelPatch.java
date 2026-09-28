package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.inventory.container.settlement.actions.workstation.ConfigureWorkstationAction;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelWorkstationActionSupport;

@ModMethodPatch(target = ConfigureWorkstationAction.class, name = "executePacket", arguments = {PacketReader.class})
public class ConfigureWorkstationMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This ConfigureWorkstationAction action, @Advice.Argument(0) PacketReader reader) {
		return SettlementLevelWorkstationActionSupport.handleConfigure(action, reader);
	}
}
