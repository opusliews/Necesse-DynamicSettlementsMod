package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.level.maps.Level;
import opusliews.worldgengating.WorldgenGatingData;
import opusliews.worldgengating.WorldgenGatingLevelData;
import opusliews.worldgengating.WorldgenLootTier;

public class PacketWorldgenGatingState extends Packet {
	public final int levelIdentifierHashCode;
	public final int objectLayerID;
	public final int tileX;
	public final int tileY;
	public final boolean active;
	public final boolean hasExplicitEntry;
	public final String objectStringID;
	public final WorldgenGatingData.NaturalType type;
	public final WorldgenLootTier tier;
	public final boolean allowPlayerPlaced;

	public PacketWorldgenGatingState(Level level, int objectLayerID, int tileX, int tileY, boolean active) {
		this.levelIdentifierHashCode = level.getIdentifierHashCode();
		this.objectLayerID = objectLayerID;
		this.tileX = tileX;
		this.tileY = tileY;
		this.active = active;
		this.hasExplicitEntry = false;
		this.objectStringID = null;
		this.type = null;
		this.tier = null;
		this.allowPlayerPlaced = false;
		writePacket();
	}

	public PacketWorldgenGatingState(Level level, WorldgenGatingLevelData.Entry entry) {
		this.levelIdentifierHashCode = level.getIdentifierHashCode();
		this.objectLayerID = entry.objectLayerID;
		this.tileX = entry.tileX;
		this.tileY = entry.tileY;
		this.active = entry.active;
		this.hasExplicitEntry = true;
		this.objectStringID = entry.objectStringID;
		this.type = entry.type;
		this.tier = entry.tier;
		this.allowPlayerPlaced = entry.allowPlayerPlaced;
		writePacket();
	}

	private void writePacket() {
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(levelIdentifierHashCode);
		writer.putNextInt(objectLayerID);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
		writer.putNextBoolean(active);
		writer.putNextBoolean(hasExplicitEntry);
		if (hasExplicitEntry) {
			writer.putNextString(objectStringID);
			writer.putNextByteUnsigned(type.ordinal());
			writer.putNextByteUnsigned(tier.ordinal());
			writer.putNextBoolean(allowPlayerPlaced);
		}
	}

	public PacketWorldgenGatingState(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		this.levelIdentifierHashCode = reader.getNextInt();
		this.objectLayerID = reader.getNextInt();
		this.tileX = reader.getNextInt();
		this.tileY = reader.getNextInt();
		this.active = reader.getNextBoolean();
		this.hasExplicitEntry = reader.getNextBoolean();
		if (hasExplicitEntry) {
			this.objectStringID = reader.getNextString();
			this.type = WorldgenGatingData.NaturalType.values()[reader.getNextByteUnsigned()];
			this.tier = WorldgenLootTier.values()[reader.getNextByteUnsigned()];
			this.allowPlayerPlaced = reader.getNextBoolean();
		}
		else {
			this.objectStringID = null;
			this.type = null;
			this.tier = null;
			this.allowPlayerPlaced = false;
		}
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();
		if (level == null || level.getIdentifierHashCode() != levelIdentifierHashCode) return;
		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(level, true);
		if (hasExplicitEntry) {
			data.applySyncedEntry(objectLayerID, tileX, tileY, objectStringID, type, tier, active, allowPlayerPlaced);
		}
		else {
			data.getOrClassifyEntry(objectLayerID, tileX, tileY);
			data.setEntryActive(objectLayerID, tileX, tileY, active, false);
		}
	}
}
