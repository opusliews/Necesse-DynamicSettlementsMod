package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.level.gameObject.DoorObject;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opusliews.breaching.ZombieBreaching;

@ModMethodPatch(target = Mob.class, name = "getPathBreakDownDamage", arguments = {LevelObject.class})
public class ZombieBreachingDamagePatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This Mob mob, @Advice.Argument(0) LevelObject lo, @Advice.Return(readOnly = false) int result) {
		if (!ZombieBreaching.isZombie(mob) || lo == null || lo.object == null) {
			return;
		}

		boolean eligibleBarrier = ZombieBreaching.isBreakableTier(lo.object)
				&& (lo.object.isFence || lo.object instanceof DoorObject);
		if (!eligibleBarrier) {
			return;
		}

		if (!ZombieBreaching.isBreachingObject(mob, lo)) {
			result = 0;
			return;
		}

		int damage = ZombieBreaching.getBreakDamageAndConfigureCooldown(mob, lo);
		if (damage > 0) {
			result = damage;
		}
	}
}
