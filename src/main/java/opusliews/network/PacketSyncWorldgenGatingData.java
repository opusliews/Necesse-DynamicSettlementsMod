package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.save.LoadData;
import necesse.level.maps.Level;
import opusliews.worldgengating.WorldgenGatingLevelData;

public class PacketSyncWorldgenGatingData extends Packet {
	public final int levelIdentifierHashCode;
	private final String saveData;

	public PacketSyncWorldgenGatingData(Level level, WorldgenGatingLevelData data) {
		this.levelIdentifierHashCode = level.getIdentifierHashCode();
		this.saveData = data.getSyncSaveData().getScript();
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(levelIdentifierHashCode);
		writer.putNextStringLong(saveData);
	}

	public PacketSyncWorldgenGatingData(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		this.levelIdentifierHashCode = reader.getNextInt();
		this.saveData = reader.getNextStringLong();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();
		if (level == null || level.getIdentifierHashCode() != levelIdentifierHashCode) return;
		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		data.applyLoadData(new LoadData(saveData));
	}
}
