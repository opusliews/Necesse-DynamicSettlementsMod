package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.gfx.GameResources;
import necesse.level.maps.Level;
import opusliews.logging.Logging;

public class PacketWarningBellRing extends Packet {
	public final int tileX;
	public final int tileY;

	public PacketWarningBellRing(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		this.tileX = reader.getNextInt();
		this.tileY = reader.getNextInt();
	}

	public PacketWarningBellRing(int tileX, int tileY) {
		this.tileX = tileX;
		this.tileY = tileY;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Logging.logMessage("WarningBell: client received ring packet for " + tileX + "," + tileY);
		Level level = client.getLevel();
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) {
			Logging.logMessage("WarningBell: client rejected ring packet because level/tile is invalid");
			return;
		}

		Logging.logMessage("WarningBell: client playing cling sound at " + tileX + "," + tileY);
		SoundManager.playSound(
				GameResources.cling,
				SoundEffect.effect(tileX * 32 + 16, tileY * 32 + 16)
						.volume(1.0F)
						.pitch(1.15F)
		);
	}
}
