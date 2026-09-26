package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import opusliews.worldgengating.WorldgenLootTier;
import opusliews.worldgengating.WorldgenSpecialLootGatingSystem;

public class PacketWorldgenMimicGatingState extends Packet {
	public final int uniqueID;
	public final WorldgenLootTier tier;
	public final boolean unlocked;

	public PacketWorldgenMimicGatingState(int uniqueID, WorldgenLootTier tier, boolean unlocked) {
		this.uniqueID = uniqueID;
		this.tier = tier;
		this.unlocked = unlocked;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(uniqueID);
		writer.putNextByteUnsigned(tier == null ? 0 : tier.ordinal() + 1);
		writer.putNextBoolean(unlocked);
	}

	public PacketWorldgenMimicGatingState(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		this.uniqueID = reader.getNextInt();
		int tierIndex = reader.getNextByteUnsigned();
		this.tier = tierIndex == 0 ? null : WorldgenLootTier.values()[tierIndex - 1];
		this.unlocked = reader.getNextBoolean();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		WorldgenSpecialLootGatingSystem.applyClientMimicState(uniqueID, tier, unlocked);
	}
}
