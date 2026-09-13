package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.sound.gameSound.GameSound;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.Mob;
import necesse.entity.projectile.Projectile;
import necesse.gfx.GameResources;
import necesse.level.gameObject.DoorObject;
import necesse.level.maps.LevelObject;
import necesse.level.maps.LevelObjectHit;
import net.bytebuddy.asm.Advice;
import opus.breaching.WarningBellSystem;
import opus.breaching.ZombieBreaching;
import opus.damage.HardcoreFeatures;
import opus.damage.WeatheringMaterialTier;
import opus.logging.Logging;

@ModMethodPatch(
		target = Projectile.class,
		name = "onHit",
		arguments = {Mob.class, LevelObjectHit.class, float.class, float.class, boolean.class, ServerClient.class}
)
public class ZombieArcherBreachingProjectilePatch {
	@Advice.OnMethodEnter
	static void onEnter(
			@Advice.This Projectile projectile,
			@Advice.Argument(1) LevelObjectHit objectHit,
			@Advice.Argument(2) float hitX,
			@Advice.Argument(3) float hitY
	) {
		if (objectHit == null || objectHit.invalidPos()) {
			return;
		}

		Mob owner = projectile.getOwner();
		if (!ZombieBreaching.isZombie(owner)) {
			return;
		}

		LevelObject lo = objectHit.getLevelObject();
		WeatheringMaterialTier tier = ZombieBreaching.getTier(lo.object);
		boolean eligibleMaterial = tier != null && tier.getTier() < 4 && (lo.object.isFence || lo.object instanceof DoorObject);
		if (!eligibleMaterial) {
			return;
		}

		if (projectile.isClient()) {
			playImpactSound(lo, tier);
			return;
		}

		if (!projectile.isServer() || !HardcoreFeatures.isServerEnabled() || !ZombieBreaching.isBreachingObject(owner, lo)) {
			return;
		}

		int damage = ZombieBreaching.getProjectileBreakDamage(owner, lo);
		if (damage > 0) {
			Logging.logMessage("WarningBell: zombie projectile hit eligible barrier at "
					+ lo.tileX + "," + lo.tileY + ", forwarding alert");
			WarningBellSystem.onZombieBarrierAttacked(owner, lo);
			lo.object.onPathBreakDown(lo.level, lo.tileX, lo.tileY, damage, owner, (int)hitX, (int)hitY);
		}
	}

	public static void playImpactSound(LevelObject lo, WeatheringMaterialTier tier) {
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
