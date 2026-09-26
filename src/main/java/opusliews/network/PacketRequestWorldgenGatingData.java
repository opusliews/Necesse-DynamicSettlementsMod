package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;
import opusliews.worldgengating.WorldgenGatingLevelData;

public class PacketRequestWorldgenGatingData extends Packet {
	public final int levelIdentifierHashCode;

	public PacketRequestWorldgenGatingData(Level level) {
		this.levelIdentifierHashCode = level.getIdentifierHashCode();
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(levelIdentifierHashCode);
	}

	public PacketRequestWorldgenGatingData(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		this.levelIdentifierHashCode = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		Level level = client.getLevel();
		if (level == null || level.getIdentifierHashCode() != levelIdentifierHashCode) return;
		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		client.sendPacket(new PacketSyncWorldgenGatingData(level, data));
	}
}
