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
import opusliews.settlement.SettlementChestProtectionSystem;

public class PacketSettlementChestProtectionRequest extends Packet {
	private final int settlementUniqueID;

	public PacketSettlementChestProtectionRequest(int settlementUniqueID) {
		this.settlementUniqueID = settlementUniqueID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
	}

	public PacketSettlementChestProtectionRequest(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!(client.getContainer() instanceof SettlementContainer)) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Rejected settings request: client is not in SettlementContainer");
			return;
		}

		SettlementContainer container = (SettlementContainer)client.getContainer();
		if (container.getSettlementUniqueID() != settlementUniqueID) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Rejected settings request: container settlement=" + container.getSettlementUniqueID() + " requested=" + settlementUniqueID);
			return;
		}

		ServerSettlementData settlement = container.getServerData();
		if (settlement == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Rejected settings request: server settlement data missing id=" + settlementUniqueID);
			return;
		}

		client.sendPacket(new PacketSettlementChestProtectionSync(settlement, SettlementChestProtectionSystem.isProtected(settlement)));
	}
}
