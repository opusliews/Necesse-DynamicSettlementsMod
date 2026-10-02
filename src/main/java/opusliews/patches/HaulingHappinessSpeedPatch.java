package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.settler.HaulingHappinessSpeedSystem;

@ModMethodPatch(target = HumanMob.class, name = "serverTick", arguments = {})
public class HaulingHappinessSpeedPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human) {
		HaulingHappinessSpeedSystem.serverTick(human);
	}
}
