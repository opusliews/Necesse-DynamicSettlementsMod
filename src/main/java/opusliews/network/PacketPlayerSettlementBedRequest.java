package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementContainer;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;
import opusliews.settlement.SettlementPlayerBedSystem;

public class PacketPlayerSettlementBedRequest extends Packet {
	private final int settlementUniqueID;

	public PacketPlayerSettlementBedRequest(int settlementUniqueID) {
		this.settlementUniqueID = settlementUniqueID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
	}

	public PacketPlayerSettlementBedRequest(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (client == null || !(client.getContainer() instanceof SettlementContainer)) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Rejected settlement sync request because client is not in SettlementContainer");
			return;
		}
		SettlementContainer container = (SettlementContainer)client.getContainer();
		if (container.getSettlementUniqueID() != settlementUniqueID) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Rejected settlement sync request because container settlement=" + container.getSettlementUniqueID() + " requested=" + settlementUniqueID + " client=" + client.getName());
			return;
		}
		ServerSettlementData settlement = container.getServerData();
		if (settlement == null || settlement.networkData == null || !settlement.networkData.doesClientHaveAccess(client)) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Rejected settlement sync request because settlement/access is unavailable requested=" + settlementUniqueID + " client=" + client.getName());
			return;
		}
		SettlementPlayerBedSystem.sendSettlementSync(settlement, client);
	}
}
