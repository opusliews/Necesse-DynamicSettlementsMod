package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.PathBreakDownLevelData;
import net.bytebuddy.asm.Advice;
import opusliews.breaching.ZombieBreachingDamageLimiter;

@ModMethodPatch(
		target = PathBreakDownLevelData.class,
		name = "getFinalDamage",
		arguments = {int.class, int.class, int.class}
)
public class ZombieBreachingFinalDamagePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	static int onEnter(@Advice.Argument(2) int damage) {
		return ZombieBreachingDamageLimiter.isBypassing() ? damage : 0;
	}

	@Advice.OnMethodExit
	static void onExit(
			@Advice.Enter int bypassDamage,
			@Advice.Return(readOnly = false) int result
	) {
		if (bypassDamage > 0) {
			result = bypassDamage;
		}
	}
}
