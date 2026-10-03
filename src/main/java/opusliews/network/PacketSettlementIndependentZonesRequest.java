package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;

public class PacketSettlementIndependentZonesRequest extends Packet {
	private final int settlementUniqueID;

	public PacketSettlementIndependentZonesRequest(int settlementUniqueID) {
		this.settlementUniqueID = settlementUniqueID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
	}

	public PacketSettlementIndependentZonesRequest(byte[] data) {
		super(data);
		settlementUniqueID = new PacketReader(this).getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(server).getServerData(settlementUniqueID);
		if (settlement == null || !settlement.networkData.doesClientHaveAccess(client)) return;
		client.sendPacket(new PacketSettlementIndependentZonesSync(settlement));
	}
}
