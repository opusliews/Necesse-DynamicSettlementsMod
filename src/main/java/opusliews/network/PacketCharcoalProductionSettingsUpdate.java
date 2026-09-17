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

public class PacketCharcoalProductionSettingsUpdate extends Packet {
	private final int settlementUniqueID;
	private final int produceUntilUnitsStocked;
	private final boolean repeatForever;

	public PacketCharcoalProductionSettingsUpdate(int settlementUniqueID, int produceUntilUnitsStocked, boolean repeatForever) {
		this.settlementUniqueID = settlementUniqueID;
		this.produceUntilUnitsStocked = Math.max(0, produceUntilUnitsStocked);
		this.repeatForever = repeatForever;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextInt(this.produceUntilUnitsStocked);
		writer.putNextBoolean(repeatForever);
	}

	public PacketCharcoalProductionSettingsUpdate(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		produceUntilUnitsStocked = Math.max(0, reader.getNextInt());
		repeatForever = reader.getNextBoolean();
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
		data.setProductionSettings(produceUntilUnitsStocked, repeatForever);
		client.sendPacket(new PacketCharcoalProductionSettingsSync(produceUntilUnitsStocked, repeatForever));
	}
}
