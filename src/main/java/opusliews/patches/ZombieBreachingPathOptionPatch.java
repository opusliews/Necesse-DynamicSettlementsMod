package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PathDoorOption;
import net.bytebuddy.asm.Advice;
import opusliews.breaching.ZombieBreaching;

@ModMethodPatch(target = Mob.class, name = "getPathDoorOption", arguments = {})
public class ZombieBreachingPathOptionPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This Mob mob, @Advice.Return(readOnly = false) PathDoorOption result) {
		if (ZombieBreaching.isZombie(mob) && mob.getLevel() != null) {
			result = ZombieBreaching.getState(mob).pathDoorOption;
		}
	}
}
