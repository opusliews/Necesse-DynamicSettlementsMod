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

public class PacketSettlementChestProtectionUpdate extends Packet {
	private final int settlementUniqueID;
	private final boolean enabled;

	public PacketSettlementChestProtectionUpdate(int settlementUniqueID, boolean enabled) {
		this.settlementUniqueID = settlementUniqueID;
		this.enabled = enabled;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextBoolean(enabled);
	}

	public PacketSettlementChestProtectionUpdate(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		enabled = reader.getNextBoolean();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!(client.getContainer() instanceof SettlementContainer)) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Rejected settings update: client is not in SettlementContainer");
			return;
		}

		SettlementContainer container = (SettlementContainer)client.getContainer();
		if (container.getSettlementUniqueID() != settlementUniqueID) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Rejected settings update: container settlement=" + container.getSettlementUniqueID() + " requested=" + settlementUniqueID);
			return;
		}

		ServerSettlementData settlement = container.getServerData();
		if (settlement == null || settlement.networkData == null || !settlement.networkData.doesClientHaveAccess(client)) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Rejected settings update: no settlement access id=" + settlementUniqueID + " client=" + client.getName());
			return;
		}

		SettlementChestProtectionSystem.setProtected(settlement, enabled);
		client.sendPacket(new PacketSettlementChestProtectionSync(settlement, enabled));
	}
}
