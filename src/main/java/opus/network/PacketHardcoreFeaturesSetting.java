package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import opus.damage.HardcoreFeatures;

public class PacketHardcoreFeaturesSetting extends Packet {
	public final boolean enabled;

	public PacketHardcoreFeaturesSetting(boolean enabled) {
		this.enabled = enabled;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextBoolean(enabled);
	}

	public PacketHardcoreFeaturesSetting(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		this.enabled = reader.getNextBoolean();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		HardcoreFeatures.setClientEnabled(enabled);
	}
}
