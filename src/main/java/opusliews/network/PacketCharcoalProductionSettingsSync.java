package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import opusliews.charcoal.CharcoalProductionClientSettings;

public class PacketCharcoalProductionSettingsSync extends Packet {
	private final int produceUntilUnitsStocked;
	private final boolean repeatForever;

	public PacketCharcoalProductionSettingsSync(int produceUntilUnitsStocked, boolean repeatForever) {
		this.produceUntilUnitsStocked = Math.max(0, produceUntilUnitsStocked);
		this.repeatForever = repeatForever;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(this.produceUntilUnitsStocked);
		writer.putNextBoolean(repeatForever);
	}

	public PacketCharcoalProductionSettingsSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		produceUntilUnitsStocked = Math.max(0, reader.getNextInt());
		repeatForever = reader.getNextBoolean();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		CharcoalProductionClientSettings.apply(produceUntilUnitsStocked, repeatForever);
	}
}
