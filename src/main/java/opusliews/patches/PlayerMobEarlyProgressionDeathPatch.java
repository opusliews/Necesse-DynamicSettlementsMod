package opusliews.patches;

import java.util.HashSet;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;

@ModMethodPatch(target = PlayerMob.class, name = "onDeath", arguments = {Attacker.class, HashSet.class})
public class PlayerMobEarlyProgressionDeathPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player) {
		EarlyHealthProgressionSystem.onPlayerDeath(player);
	}
}
