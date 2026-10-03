package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.actions.DeleteRestrictZoneAction;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.zones.SettlementIndependentZoneActionSupport;

@ModMethodPatch(target = DeleteRestrictZoneAction.class, name = "executePacket", arguments = {PacketReader.class, ServerSettlementData.class, ServerClient.class})
public class DeleteRestrictZoneIndependentPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(0) PacketReader reader, @Advice.Argument(1) ServerSettlementData data) {
		return SettlementIndependentZoneActionSupport.handleDeleteRestrict(reader, data);
	}
}
