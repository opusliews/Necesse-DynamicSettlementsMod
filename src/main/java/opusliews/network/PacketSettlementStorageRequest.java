package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

public class PacketSettlementStorageRequest extends Packet {
	private final int settlementUniqueID;

	public PacketSettlementStorageRequest(int settlementUniqueID) {
		this.settlementUniqueID = settlementUniqueID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
	}

	public PacketSettlementStorageRequest(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!(client.getContainer() instanceof SettlementDependantContainer)) return;
		SettlementDependantContainer container = (SettlementDependantContainer)client.getContainer();
		ServerSettlementData settlement = container.getServerData();
		if (settlement == null || settlement.uniqueID != settlementUniqueID || !settlement.networkData.doesClientHaveAccess(client)) return;
		Level level = server.world.getLevel(client);
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (level == null || domain == null || domain.getLevelType(level.getIdentifier()) == null) return;
		boolean custom = !level.getIdentifier().equals(settlement.getLevel().getIdentifier());
		client.sendPacket(new PacketSettlementStorageSync(settlementUniqueID, level.getIdentifierHashCode(), custom,
				SettlementLevelStorageManager.getStoragePositions(settlement, level.getIdentifier())));
	}
}
