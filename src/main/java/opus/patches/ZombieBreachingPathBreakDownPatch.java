package opus.patches;

import java.util.function.Consumer;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opus.breaching.ZombieBreaching;
import opus.breaching.ZombieBreachingDamageLimiter;
import opus.damage.HardcoreFeatures;

@ModMethodPatch(
		target = Mob.class,
		name = "pathBreakDown",
		arguments = {LevelObject.class, boolean.class, Consumer.class}
)
public class ZombieBreachingPathBreakDownPatch {
	@Advice.OnMethodEnter
	static boolean onEnter(@Advice.This Mob mob, @Advice.Argument(0) LevelObject lo) {
		boolean bypass = mob.isServer()
				&& HardcoreFeatures.isServerEnabled()
				&& ZombieBreaching.isBreachingObject(mob, lo);

		if (bypass) {
			ZombieBreachingDamageLimiter.beginBypass();
		}

		return bypass;
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	static void onExit(@Advice.Enter boolean bypass) {
		if (bypass) {
			ZombieBreachingDamageLimiter.endBypass();
		}
	}
}
