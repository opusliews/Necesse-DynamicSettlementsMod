package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.trapdoor.TrapdoorSystem;

@ModMethodPatch(target = PlayerMob.class, name = "canBeHit", arguments = {Attacker.class})
public class TrapdoorPlayerCanBeHitPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player, @Advice.Return(readOnly = false) boolean result) {
		if (TrapdoorSystem.isHidden(player)) result = false;
	}
}
