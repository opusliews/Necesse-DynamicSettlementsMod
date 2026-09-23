package opusliews.network;

import java.util.LinkedHashMap;
import java.util.Map;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import opusliews.clayfiring.ClayFiringClientSettings;

public class PacketClayFiringSettingsSync extends Packet {
	private final LinkedHashMap<String, Integer> targets;
	private final boolean repeatForever;

	public PacketClayFiringSettingsSync(Map<String, Integer> targets, boolean repeatForever) {
		this.targets = PacketClayFiringSettingsUpdate.sanitize(targets);
		this.repeatForever = repeatForever;
		PacketWriter writer = new PacketWriter(this);
		PacketClayFiringSettingsUpdate.writeTargets(writer, this.targets);
		writer.putNextBoolean(repeatForever);
	}

	public PacketClayFiringSettingsSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		targets = PacketClayFiringSettingsUpdate.sanitize(PacketClayFiringSettingsUpdate.readTargets(reader));
		repeatForever = reader.getNextBoolean();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		ClayFiringClientSettings.apply(targets, repeatForever);
	}
}
