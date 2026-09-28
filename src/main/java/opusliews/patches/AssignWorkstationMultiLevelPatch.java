package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.actions.workstation.AssignWorkstationAction;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementLevelWorkstationActionSupport;

@ModMethodPatch(target = AssignWorkstationAction.class, name = "executePacket", arguments = {PacketReader.class})
public class AssignWorkstationMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This AssignWorkstationAction action, @Advice.Argument(0) PacketReader reader) {
		if (action == null || action.container == null || !action.container.client.isServer()) return false;
		ServerClient client = action.container.client.getServerClient();
		ServerSettlementData settlement = action.container.getServerData();
		Level level = client == null ? null : client.getLevel();
		if (!SettlementLevelWorkstationActionSupport.isCustomSettlementLevel(settlement, level)) return false;
		int x = reader.getNextInt();
		int y = reader.getNextInt();
		if (settlement.networkData.doesClientHaveAccess(client)) SettlementLevelStorageManager.assignWorkstation(settlement, level, x, y);
		return true;
	}
}
