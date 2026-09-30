package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import necesse.gfx.drawOptions.human.HumanDrawOptions;
import net.bytebuddy.asm.Advice;
import opusliews.raids.RaidPreparingProtectionSystem;

@ModMethodPatch(target = HumanDrawOptions.class, name = "applyEnemyTracker", arguments = {Mob.class, PlayerMob.class})
public class RaiderPreparingDrawPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) Mob targetMob, @Advice.Return HumanDrawOptions result) {
		if (targetMob instanceof ItemAttackerRaiderMob && RaidPreparingProtectionSystem.isProtected((ItemAttackerRaiderMob)targetMob)) {
			result.hasGlowEffect(true, targetMob.getID());
		}
	}
}
