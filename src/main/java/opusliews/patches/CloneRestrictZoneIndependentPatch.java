package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.actions.CloneRestrictZoneAction;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementIndependentZoneActionSupport;

@ModMethodPatch(target = CloneRestrictZoneAction.class, name = "executePacket", arguments = {PacketReader.class, ServerSettlementData.class, ServerClient.class})
public class CloneRestrictZoneIndependentPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(0) PacketReader reader, @Advice.Argument(1) ServerSettlementData data, @Advice.Argument(2) ServerClient client) {
		return SettlementIndependentZoneActionSupport.handleCloneRestrict(reader, data, client);
	}
}
