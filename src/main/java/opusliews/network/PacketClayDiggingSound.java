package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.util.GameRandom;
import necesse.level.maps.Level;
import opusliews.DynamicSettlements;

public class PacketClayDiggingSound extends Packet {
	public final int tileX;
	public final int tileY;
	public final boolean breaking;

	public PacketClayDiggingSound(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		this.tileX = reader.getNextInt();
		this.tileY = reader.getNextInt();
		this.breaking = reader.getNextBoolean();
	}

	public PacketClayDiggingSound(int tileX, int tileY, boolean breaking) {
		this.tileX = tileX;
		this.tileY = tileY;
		this.breaking = breaking;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
		writer.putNextBoolean(breaking);
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) {
			return;
		}

		if (!breaking) {
			double pitch = GameRandom.globalRandom.nextDouble(0.9,1.1);
			SoundManager.playSound(
					DynamicSettlements.clayDigFastSound,
					SoundEffect
							.effect(tileX * 32 + 16, tileY * 32 + 16)
							.pitch((float)pitch).volume(1.0F)
			);
		}
		else {
			SoundManager.playSound(
					DynamicSettlements.clayDigSound,
					SoundEffect.effect(tileX * 32 + 16, tileY * 32 + 16).volume(1.0F)
			);
		}
	}
}
