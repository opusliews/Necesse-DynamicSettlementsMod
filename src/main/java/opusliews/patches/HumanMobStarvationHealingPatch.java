package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.SettlerStarvationSystem;

@ModMethodPatch(
		target = HumanMob.class,
		name = "setHealthHidden",
		arguments = {int.class, float.class, float.class, Attacker.class, boolean.class}
)
public class HumanMobStarvationHealingPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This HumanMob human,
			@Advice.Argument(value = 0, readOnly = false) int health
	) {
		if (SettlerStarvationSystem.shouldBlockHealing(human, health)) {
			SettlerStarvationSystem.logBlockedHealing(human, health);
			health = human.getHealth();
		}
	}
}
