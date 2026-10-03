package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.inventory.container.settlement.actions.zones.CreateNewWorkZoneAction;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementIndependentZoneActionSupport;

@ModMethodPatch(target = CreateNewWorkZoneAction.class, name = "executePacket", arguments = {PacketReader.class})
public class CreateWorkZoneIndependentPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This CreateNewWorkZoneAction action, @Advice.Argument(0) PacketReader reader) {
		return SettlementIndependentZoneActionSupport.handleCreateWorkZone(action.container, reader);
	}
}
