package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlerBedReservationSystem;

@ModMethodPatch(target = HumanMob.class, name = "setHealthHidden", arguments = {int.class, float.class, float.class, Attacker.class, boolean.class})
public class HumanDownedBedReservationPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This HumanMob human, @Advice.Argument(0) int health) {
		if (health > 0 || human == null || !human.isServer() || !human.isSettler() || human.isDowned()) return;
		if (human.getWorldSettings() == null || human.getWorldSettings().canSettlersDie) return;
		SettlerBedReservationSystem.captureBeforeDowned(human);
	}
}
