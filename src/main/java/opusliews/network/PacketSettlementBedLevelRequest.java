package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.multilevelsettlement.SettlementBedLevelIndicatorSystem;
import opusliews.logging.Logging;

public class PacketSettlementBedLevelRequest extends Packet {
	private final int settlementUniqueID;

	public PacketSettlementBedLevelRequest(int settlementUniqueID) {
		this.settlementUniqueID = settlementUniqueID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
	}

	public PacketSettlementBedLevelRequest(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (Logging.logEnabled) Logging.logMessage("[BedLevelIndicator] Server received sync request settlement=" + settlementUniqueID + " container=" + (client == null || client.getContainer() == null ? "null" : client.getContainer().getClass().getName()));
		if (client == null || !(client.getContainer() instanceof SettlementDependantContainer)) {
			if (Logging.logEnabled) Logging.logMessage("[BedLevelIndicator] Request rejected: active container is not SettlementDependantContainer");
			return;
		}
		SettlementDependantContainer container = (SettlementDependantContainer)client.getContainer();
		ServerSettlementData settlement = container.getServerData();
		if (settlement == null) {
			if (Logging.logEnabled) Logging.logMessage("[BedLevelIndicator] Request rejected: settlement is null");
			return;
		}
		if (settlement.uniqueID != settlementUniqueID) {
			if (Logging.logEnabled) Logging.logMessage("[BedLevelIndicator] Request rejected: settlement ID mismatch requested=" + settlementUniqueID + " actual=" + settlement.uniqueID);
			return;
		}
		if (!settlement.networkData.doesClientHaveAccess(client)) {
			if (Logging.logEnabled) Logging.logMessage("[BedLevelIndicator] Request rejected: client has no settlement access settlement=" + settlement.uniqueID);
			return;
		}
		client.sendPacket(SettlementBedLevelIndicatorSystem.getSyncPacket(settlement));
		if (Logging.logEnabled) Logging.logMessage("[BedLevelIndicator] Server sent bed-level sync settlement=" + settlement.uniqueID);
	}

}
