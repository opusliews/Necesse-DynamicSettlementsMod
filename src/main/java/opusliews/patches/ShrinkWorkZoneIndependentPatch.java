package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.inventory.container.settlement.actions.zones.ShrinkWorkZoneAction;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementIndependentZoneActionSupport;

@ModMethodPatch(target = ShrinkWorkZoneAction.class, name = "executePacket", arguments = {PacketReader.class})
public class ShrinkWorkZoneIndependentPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This ShrinkWorkZoneAction action, @Advice.Argument(0) PacketReader reader) {
		return SettlementIndependentZoneActionSupport.handleShrinkWorkZone(action.container, reader);
	}
}
