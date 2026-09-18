package opusliews.tile;

import java.awt.Color;
import java.util.List;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.util.GameRandom;
import necesse.engine.util.GameUtils;
import necesse.entity.particle.Particle;
import necesse.entity.particle.ParticleOption;
import necesse.gfx.GameResources;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.drawables.LevelTileLiquidDrawOptions;
import necesse.gfx.drawables.LevelTileTerrainDrawOptions;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.level.maps.Level;

public class BurningUnfiredBrickPitTile extends UnfiredBrickLogPitTile {
	public static final String stringID = "burningunfiredbrickpittile";

	public BurningUnfiredBrickPitTile() {
		super();
		mapColor = new Color(82, 61, 48);
		lightLevel = 90;
		lightHue = 35.0F;
		lightSat = 0.65F;
	}

	@Override
	public void tickEffect(Level level, int tileX, int tileY) {
		if (!level.isClient()) {
			return;
		}

		for (int i = 0; i < 4; i++) {
			if (GameRandom.globalRandom.getChance(0.55F)) {
				ParticleOption particle = level.entityManager.addParticle(
						tileX * 32.0F + GameRandom.globalRandom.getFloatBetween(0.0F,32.0F),
						tileY * 32.0F + GameRandom.globalRandom.getFloatBetween(0.0F, 32.0F),
						GameRandom.globalRandom.getChance(0.75F) ? Particle.GType.CRITICAL : Particle.GType.COSMETIC
				)
						.movesConstant(
								GameRandom.globalRandom.getFloatBetween(-0.7F, 0.7F),
								GameRandom.globalRandom.getFloatBetween(-0.5F, 1.5F)
						)
						.heightMoves(0.0F, 12.0F)
						.flameColor()
						.sizeFades(10, 15)
						.lifeTime(1800);

				if (GameRandom.globalRandom.getChance(0.35F)) {
					particle.onProgress(0.55F, p -> level.entityManager.addParticle(
							p.x + GameRandom.globalRandom.getFloatBetween(-2.0F, 2.0F),
							p.y,
							Particle.GType.COSMETIC
					)
							.smokeColor()
							.sizeFades(8, 13)
							.heightMoves(5.0F, 22.0F)
							.lifeTime(1600));
				}
			}

			if (GameRandom.globalRandom.getChance(0.035F)) {
				SoundManager.playSound(
						GameResources.campfireAmbient,
						SoundEffect.effect(tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F).volume(0.55F)
				);
			}

			if (GameRandom.globalRandom.getChance(0.012F)) {
				SoundManager.playSound(
						GameResources.campfireSizzle,
						SoundEffect.effect(tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F).volume(0.35F)
				);
			}
			}
	}
}
