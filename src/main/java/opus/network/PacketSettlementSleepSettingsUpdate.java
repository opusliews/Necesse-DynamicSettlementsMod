package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementContainer;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opus.sleep.SettlementSleepSettings;
import opus.sleep.SettlementSleepSettingsLevelData;

public class PacketSettlementSleepSettingsUpdate extends Packet {
	private final int settlementUniqueID;
	private final SettlementSleepSettings settings;

	public PacketSettlementSleepSettingsUpdate(int settlementUniqueID, SettlementSleepSettings settings) {
		this.settlementUniqueID = settlementUniqueID;
		this.settings = settings == null ? SettlementSleepSettings.defaults : settings;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextBoolean(this.settings.wakeOnRaid);
		writer.putNextBoolean(this.settings.wakeOnBarrierAttack);
		writer.putNextBoolean(this.settings.wakeOnBarrierBreach);
	}

	public PacketSettlementSleepSettingsUpdate(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		settings = new SettlementSleepSettings(
				reader.getNextBoolean(),
				reader.getNextBoolean(),
				reader.getNextBoolean()
		);
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
		SettlementSleepSettingsLevelData.get(level, true).setSettings(settlementUniqueID, settings);
		client.sendPacket(new PacketSettlementSleepSettingsSync(settlementUniqueID, settings));
	}
}
