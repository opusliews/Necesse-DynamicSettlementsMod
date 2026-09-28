package opusliews.multilevelsettlement;

import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.actions.workstation.ConfigureWorkstationAction;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.settlement.events.SettlementWorkstationEvent;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementWorkstation;
import opusliews.logging.Logging;

public final class SettlementLevelWorkstationActionSupport {
	private SettlementLevelWorkstationActionSupport() {
	}

	public static boolean handleConfigure(ConfigureWorkstationAction action, PacketReader reader) {
		if (action == null || reader == null || action.container == null || !action.container.client.isServer()) return false;
		ServerClient client = action.container.client.getServerClient();
		ServerSettlementData settlement = action.container.getServerData();
		if (client == null || settlement == null) return false;
		Level level = client.getLevel();
		if (!isCustomSettlementLevel(settlement, level)) return false;

		int x = reader.getNextInt();
		int y = reader.getNextInt();
		Packet content = reader.getNextContentPacket();
		if (!settlement.networkData.doesClientHaveAccess(client)) {
			new SettlementDataEvent(settlement).applyAndSendToClient(client);
			return true;
		}

		SettlementWorkstation workstation = SettlementLevelStorageManager.getWorkstation(settlement, level.getIdentifier(), x, y);
		if (workstation == null) return true;
		action.handleAction(new PacketReader(content), settlement, workstation);
		SettlementLevelStorageManager.persistWorkstationRecipes(settlement, workstation);
		new SettlementWorkstationEvent(settlement, workstation).applyAndSendToClient(client);
		if (Logging.logEnabled) Logging.logMessage("[MultiLevelWorkstation] Applied cave workstation config action="
				+ action.getClass().getSimpleName() + " settlement=" + settlement.uniqueID + " level=" + level.getIdentifier()
				+ " tile=" + x + "," + y + " recipes=" + workstation.recipes.size());
		return true;
	}

	public static boolean isCustomSettlementLevel(ServerSettlementData settlement, Level level) {
		if (settlement == null || level == null) return false;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		return domain != null && domain.getLevelType(level.getIdentifier()) != null
				&& !level.getIdentifier().equals(settlement.getLevel().getIdentifier());
	}
}
