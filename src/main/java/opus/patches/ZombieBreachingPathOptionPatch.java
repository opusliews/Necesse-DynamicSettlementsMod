package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PathDoorOption;
import net.bytebuddy.asm.Advice;
import opus.breaching.ZombieBreaching;
import opus.damage.HardcoreFeatures;

@ModMethodPatch(target = Mob.class, name = "getPathDoorOption", arguments = {})
public class ZombieBreachingPathOptionPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This Mob mob, @Advice.Return(readOnly = false) PathDoorOption result) {
		if (HardcoreFeatures.isServerEnabled() && ZombieBreaching.isZombie(mob) && mob.getLevel() != null) {
			result = ZombieBreaching.getState(mob).pathDoorOption;
		}
	}
}
