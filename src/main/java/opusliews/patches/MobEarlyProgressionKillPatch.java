package opusliews.patches;

import java.util.HashSet;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;

@ModMethodPatch(target = Mob.class, name = "onDeath", arguments = {Attacker.class, HashSet.class})
public class MobEarlyProgressionKillPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This Mob mob, @Advice.Argument(1) HashSet attackers) {
		EarlyHealthProgressionSystem.onMobDeath(mob, attackers);
	}
}
