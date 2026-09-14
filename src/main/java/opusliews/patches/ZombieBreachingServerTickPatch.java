package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import opusliews.breaching.ZombieBreaching;

@ModMethodPatch(target = Mob.class, name = "serverTick", arguments = {})
public class ZombieBreachingServerTickPatch {
	@Advice.OnMethodEnter
	static void onEnter(@Advice.This Mob mob) {
		ZombieBreaching.serverTick(mob);
	}
}
