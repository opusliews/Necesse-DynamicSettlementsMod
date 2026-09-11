package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import opus.breaching.ZombieBreaching;
import opus.damage.HardcoreFeatures;

@ModMethodPatch(target = Mob.class, name = "serverTick", arguments = {})
public class ZombieBreachingServerTickPatch {
	@Advice.OnMethodEnter
	static void onEnter(@Advice.This Mob mob) {
		if (HardcoreFeatures.isServerEnabled()) {
			ZombieBreaching.serverTick(mob);
		}
	}
}
