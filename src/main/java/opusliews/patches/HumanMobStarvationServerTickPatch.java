package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.SettlerStarvationSystem;

@ModMethodPatch(target = HumanMob.class, name = "serverTick", arguments = {})
public class HumanMobStarvationServerTickPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human) {
		SettlerStarvationSystem.serverTick(human);
	}
}
