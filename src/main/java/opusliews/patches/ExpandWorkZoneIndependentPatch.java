package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.inventory.container.settlement.actions.zones.ExpandWorkZoneAction;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementIndependentZoneActionSupport;

@ModMethodPatch(target = ExpandWorkZoneAction.class, name = "executePacket", arguments = {PacketReader.class})
public class ExpandWorkZoneIndependentPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This ExpandWorkZoneAction action, @Advice.Argument(0) PacketReader reader) {
		return SettlementIndependentZoneActionSupport.handleExpandWorkZone(action.container, reader);
	}
}
