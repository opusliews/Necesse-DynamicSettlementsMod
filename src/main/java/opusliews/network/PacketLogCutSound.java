package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.util.GameRandom;
import necesse.gfx.GameResources;
import necesse.level.maps.Level;
import opusliews.logging.Logging;

public class PacketLogCutSound extends Packet {
	public final int tileX;
	public final int tileY;

	public PacketLogCutSound(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		this.tileX = reader.getNextInt();
		this.tileY = reader.getNextInt();
	}

	public PacketLogCutSound(int tileX, int tileY) {
		this.tileX = tileX;
		this.tileY = tileY;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) {
			return;
		}

		double pitch = GameRandom.globalRandom.nextDouble(0.8,1.2);

		SoundManager.playSound(
				GameResources.tap,
				SoundEffect.effect(tileX * 32 + 16, tileY * 32 + 16)
						.volume(1.0F)
						.pitch((float)pitch)
		);
	}
}
