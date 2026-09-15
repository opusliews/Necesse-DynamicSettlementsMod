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

public class PacketAnvilCraftingSound extends Packet {
	private final int tileX;
	private final int tileY;

	public PacketAnvilCraftingSound(int tileX, int tileY) {
		this.tileX = tileX;
		this.tileY = tileY;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
	}

	public PacketAnvilCraftingSound(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) {
			return;
		}

		int variant = GameRandom.globalRandom.nextInt(3);
		SoundManager.playSound(
				variant == 0 ? GameResources.tap : variant == 1 ? GameResources.tap2 : GameResources.anvilOpen,
				SoundEffect.effect(tileX * 32 + 16, tileY * 32 + 16)
						.volume(GameRandom.globalRandom.getFloatBetween(0.16F, 0.26F))
		);
	}
}
