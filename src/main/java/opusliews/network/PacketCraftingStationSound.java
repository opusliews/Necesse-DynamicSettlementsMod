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

public class PacketCraftingStationSound extends Packet {
	public static final int WORKSTATION = 0;
	public static final int CARPENTER = 1;
	public static final int ALCHEMY = 2;
	public static final int ANVIL = 3;

	private final int tileX;
	private final int tileY;
	private final int soundType;

	public PacketCraftingStationSound(int tileX, int tileY, int soundType) {
		this.tileX = tileX;
		this.tileY = tileY;
		this.soundType = soundType;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
		writer.putNextByteUnsigned(soundType);
	}

	public PacketCraftingStationSound(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
		soundType = reader.getNextByteUnsigned();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) return;

		float x = tileX * 32 + 16;
		float y = tileY * 32 + 16;

		if (soundType == ANVIL) {
			int variant = GameRandom.globalRandom.nextInt(3);
			SoundManager.playSound(
					variant == 0 ? GameResources.tap : variant == 1 ? GameResources.tap2 : GameResources.anvilOpen,
					SoundEffect.effect(x, y)
							.volume(GameRandom.globalRandom.getFloatBetween(0.16F, 0.26F))
			);
			return;
		}

		if (soundType == CARPENTER) {
			int variant = GameRandom.globalRandom.nextInt(5);
			SoundManager.playSound(
					variant == 0 ? GameResources.tap
							: variant == 1 ? GameResources.tap2
							: variant == 2 ? GameResources.cratebreak1
							: variant == 3 ? GameResources.cratebreak2
							: GameResources.cratebreak3,
					SoundEffect.effect(x, y)
							.volume(GameRandom.globalRandom.getFloatBetween(0.10F, 0.18F))
							.pitch(GameRandom.globalRandom.getFloatBetween(1.15F, 1.45F))
			);
			return;
		}

		if (soundType == ALCHEMY) {
			int variant = GameRandom.globalRandom.nextInt(4);
			if (variant <= 1) {
				SoundManager.playSound(
						GameResources.fizz,
						SoundEffect.effect(x, y)
								.volume(GameRandom.globalRandom.getFloatBetween(0.08F, 0.15F))
								.pitch(GameRandom.globalRandom.getFloatBetween(0.9F, 1.2F))
				);
			} else if (variant == 2) {
				SoundManager.playSound(
						GameResources.alchemyTableOpen,
						SoundEffect.effect(x, y)
								.volume(GameRandom.globalRandom.getFloatBetween(0.08F, 0.13F))
								.pitch(GameRandom.globalRandom.getFloatBetween(0.95F, 1.08F))
				);
			} else {
				SoundManager.playSound(
						GameResources.waterblob,
						SoundEffect.effect(x, y)
								.volume(GameRandom.globalRandom.getFloatBetween(0.07F, 0.12F))
								.pitch(GameRandom.globalRandom.getFloatBetween(1.0F, 1.3F))
				);
			}
			return;
		}

		int variant = GameRandom.globalRandom.nextInt(3);
		SoundManager.playSound(
				variant == 0 ? GameResources.tap : variant == 1 ? GameResources.tick : GameResources.pop,
				SoundEffect.effect(x, y)
						.volume(GameRandom.globalRandom.getFloatBetween(0.08F, 0.14F))
						.pitch(GameRandom.globalRandom.getFloatBetween(0.95F, 1.15F))
		);
	}
}
