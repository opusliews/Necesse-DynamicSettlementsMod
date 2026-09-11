package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.sound.gameSound.GameSound;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.Mob;
import necesse.gfx.GameResources;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opus.breaching.ZombieBreaching;
import opus.damage.WeatheringMaterialTier;

@ModMethodPatch(target = Mob.class, name = "onPathBreakDownHit", arguments = {LevelObject.class, boolean.class, boolean.class})
public class ZombieBreachingSoundPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This Mob mob, @Advice.Argument(0) LevelObject lo) {
		if (!mob.isClient() || !ZombieBreaching.isZombie(mob)) {
			return;
		}

		WeatheringMaterialTier tier = ZombieBreaching.getTier(lo.object);
		if (tier == null || tier.getTier() >= 4) {
			return;
		}

		GameSound sound;
		float pitch;
		if (tier == WeatheringMaterialTier.WOOD) {
			sound = GameRandom.globalRandom.getOneOf(GameResources.cratebreak1, GameResources.cratebreak2, GameResources.cratebreak3);
			pitch = GameRandom.globalRandom.getFloatBetween(0.75F, 0.95F);
		}
		else if (ZombieBreaching.isMetalObject(lo.object)) {
			sound = GameResources.cling;
			pitch = GameRandom.globalRandom.getFloatBetween(0.75F, 0.95F);
		}
		else {
			sound = GameResources.blunthit;
			pitch = GameRandom.globalRandom.getFloatBetween(0.55F, 0.75F);
		}

		SoundManager.playSound(sound, SoundEffect.effect(lo.tileX * 32 + 16, lo.tileY * 32 + 16).volume(0.65F).pitch(pitch));
	}
}
