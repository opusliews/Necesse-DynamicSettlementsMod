package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.fishing.FishingAreaLevelData;

public class PacketFishingAreasRequest extends Packet {
	private final int settlementUniqueID;

	public PacketFishingAreasRequest(int settlementUniqueID) {
		this.settlementUniqueID = settlementUniqueID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
	}

	public PacketFishingAreasRequest(byte[] data) {
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
		FishingAreaLevelData data = FishingAreaLevelData.get(settlement.getLevel(), false);
		FishingAreaLevelData.Snapshot snapshot = data == null
				? new FishingAreaLevelData.Snapshot(new java.util.ArrayList<>(), new java.util.HashMap<>())
				: data.snapshot(settlementUniqueID);
		client.sendPacket(new PacketFishingAreasSync(settlementUniqueID, snapshot));
	}
}
