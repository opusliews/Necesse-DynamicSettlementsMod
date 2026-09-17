package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.inventory.container.settlement.SettlementContainer;
import opusliews.tile.CharcoalPitLevelData;

public class PacketCharcoalProductionSettingsRequest extends Packet {
	private final int settlementUniqueID;

	public PacketCharcoalProductionSettingsRequest(int settlementUniqueID) {
		this.settlementUniqueID = settlementUniqueID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
	}

	public PacketCharcoalProductionSettingsRequest(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!(client.getContainer() instanceof SettlementContainer)) {
			return;
		}

		SettlementContainer container = (SettlementContainer)client.getContainer();
		if (container.getSettlementUniqueID() != settlementUniqueID) {
			return;
		}

		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(server).getOrLoadServerData(settlementUniqueID);
		if (settlement == null) {
			return;
		}

		CharcoalPitLevelData data = CharcoalPitLevelData.get(settlement.getLevel(), true);
		client.sendPacket(new PacketCharcoalProductionSettingsSync(data.getProduceUntilUnitsStocked(), data.isRepeatForever()));
	}
}
