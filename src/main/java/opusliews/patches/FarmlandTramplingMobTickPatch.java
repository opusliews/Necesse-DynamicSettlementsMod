package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import opusliews.farming.FarmlandTramplingSystem;

@ModMethodPatch(target = Mob.class, name = "serverTick", arguments = {})
public class FarmlandTramplingMobTickPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This Mob mob) {
		FarmlandTramplingSystem.serverTick(mob);
	}
}
