package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import opusliews.forms.SettlementSleepSettingsFormSystem;
import opusliews.sleep.SettlementSleepSettings;

public class PacketSettlementSleepSettingsSync extends Packet {
	private final int settlementUniqueID;
	private final SettlementSleepSettings settings;

	public PacketSettlementSleepSettingsSync(int settlementUniqueID, SettlementSleepSettings settings) {
		this.settlementUniqueID = settlementUniqueID;
		this.settings = settings == null ? SettlementSleepSettings.defaults : settings;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextBoolean(this.settings.wakeOnRaid);
		writer.putNextBoolean(this.settings.wakeOnBarrierAttack);
		writer.putNextBoolean(this.settings.wakeOnBarrierBreach);
	}

	public PacketSettlementSleepSettingsSync(byte[] data) {
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
	public void processClient(NetworkPacket packet, Client client) {
		SettlementSleepSettingsFormSystem.applyServerSettings(settlementUniqueID, settings);
	}
}
