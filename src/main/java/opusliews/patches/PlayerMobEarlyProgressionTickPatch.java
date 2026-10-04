package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;
import opusliews.progression.GuideProgressionSystem;

@ModMethodPatch(target = PlayerMob.class, name = "serverTick", arguments = {})
public class PlayerMobEarlyProgressionTickPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PlayerMob player) {
		EarlyHealthProgressionSystem.serverTick(player);
		GuideProgressionSystem.serverTick(player);
	}
}
