package opusliews.zones;

import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.network.PacketSettlementIndependentZonesSync;

public final class SettlementIndependentZoneNetwork {
	private SettlementIndependentZoneNetwork() {
	}

	public static void broadcast(ServerSettlementData settlement) {
		if (settlement == null || settlement.getServer() == null) return;
		Server server = settlement.getServer();
		PacketSettlementIndependentZonesSync sync = new PacketSettlementIndependentZonesSync(settlement);
		for (Object value : server.getClients()) {
			if (!(value instanceof ServerClient)) continue;
			ServerClient target = (ServerClient)value;
			if (!(target.getContainer() instanceof SettlementDependantContainer)) continue;
			SettlementDependantContainer container = (SettlementDependantContainer)target.getContainer();
			ServerSettlementData targetSettlement = container.getServerData();
			if (targetSettlement != null && targetSettlement.uniqueID == settlement.uniqueID && settlement.networkData.doesClientHaveAccess(target)) target.sendPacket(sync);
		}
	}
}
