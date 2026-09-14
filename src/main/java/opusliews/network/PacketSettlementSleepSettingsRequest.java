package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementContainer;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.sleep.SettlementSleepSettings;
import opusliews.sleep.SettlementSleepSettingsLevelData;

public class PacketSettlementSleepSettingsRequest extends Packet {
	private final int settlementUniqueID;

	public PacketSettlementSleepSettingsRequest(int settlementUniqueID) {
		this.settlementUniqueID = settlementUniqueID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
	}

	public PacketSettlementSleepSettingsRequest(byte[] data) {
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
		if (container.getSettlementUniqueID() != settlementUniqueID || !container.isSettlementOwner(client)) {
			return;
		}

		ServerSettlementData settlement = container.getServerData();
		if (settlement == null) {
			return;
		}

		Level level = settlement.getLevel();
		SettlementSleepSettingsLevelData data = SettlementSleepSettingsLevelData.get(level, false);
		SettlementSleepSettings settings = data == null
				? SettlementSleepSettings.defaults
				: data.getSettings(settlementUniqueID);
		client.sendPacket(new PacketSettlementSleepSettingsSync(settlementUniqueID, settings));
	}
}
