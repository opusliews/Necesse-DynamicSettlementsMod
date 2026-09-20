package opusliews.tile;

import java.awt.Color;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.Mob;
import necesse.entity.particle.Particle;
import necesse.gfx.GameResources;
import necesse.level.maps.Level;

public class BurningCharcoalPitTile extends CoveredCharcoalPitTile {
	public static final String stringID = "burningcharcoalpittile";

	public BurningCharcoalPitTile() {
		super();
		mapColor = new Color(82, 61, 48);
	}

	@Override
	public void tickEffect(Level level, int tileX, int tileY) {
		super.tickEffect(level, tileX, tileY);

		float centerX = tileX * 32.0F + 16.0F;
		float centerY = tileY * 32.0F + 16.0F;

		if (GameRandom.globalRandom.getChance(0.68F)) {
			level.entityManager.addParticle(
					centerX + GameRandom.globalRandom.getFloatBetween(-7.0F, 7.0F),
					centerY + GameRandom.globalRandom.getFloatBetween(-5.0F, 5.0F),
					Particle.GType.COSMETIC
			).smokeColor()
					.sizeFades(5, 10)
					.movesConstant(
							GameRandom.globalRandom.getFloatBetween(-1.2F, 1.2F),
							GameRandom.globalRandom.getFloatBetween(-0.7F, 0.7F)
					)
					.heightMoves(2.0F, 22.0F)
					.lifeTime(GameRandom.globalRandom.getIntBetween(1200, 1900));
		}

		if (GameRandom.globalRandom.getChance(0.255F)) {
			level.entityManager.addParticle(
					centerX + GameRandom.globalRandom.getFloatBetween(-8.0F, 8.0F),
					centerY + GameRandom.globalRandom.getFloatBetween(-6.0F, 6.0F),
					Particle.GType.IMPORTANT_COSMETIC
			).color(new Color(235, GameRandom.globalRandom.getIntBetween(90, 150), 18))
					.givesLight(35.0F, 0.8F)
					.sizeFades(2, 4)
					.movesConstant(
							GameRandom.globalRandom.getFloatBetween(-2.0F, 2.0F),
							GameRandom.globalRandom.getFloatBetween(-1.0F, 1.0F)
					)
					.heightMoves(1.0F, GameRandom.globalRandom.getFloatBetween(10.0F, 22.0F))
					.lifeTime(GameRandom.globalRandom.getIntBetween(500, 900));
		}

		if (GameRandom.globalRandom.getChance(0.012F)) {
			SoundManager.playSound(
					GameResources.campfireAmbient,
					SoundEffect.effect(centerX, centerY).volume(0.3F).pitch(GameRandom.globalRandom.getFloatBetween(0.92F, 1.05F))
			);
		}
	}

	@Override
	public void tick(Mob mob, Level level, int tileX, int tileY) {
		FireTileDamage.tick(mob, level);
	}
}
