package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.RaidPreparingVisibilitySystem;

public class RaiderPreparingHiddenMobPatch {
	@ModMethodPatch(target = Mob.class, name = "isVisible", arguments = {})
	public static class VisiblePatch {
		@Advice.OnMethodExit
		public static void onExit(@Advice.This Mob mob, @Advice.Return(readOnly = false) boolean result) {
			if (mob instanceof ItemAttackerRaiderMob && RaidPreparingVisibilitySystem.isHidden((ItemAttackerRaiderMob)mob)) result = false;
		}
	}

	@ModMethodPatch(target = Mob.class, name = "canBeHit", arguments = {Attacker.class})
	public static class CanBeHitPatch {
		@Advice.OnMethodExit
		public static void onExit(@Advice.This Mob mob, @Advice.Return(readOnly = false) boolean result) {
			if (mob instanceof ItemAttackerRaiderMob && RaidPreparingVisibilitySystem.isHidden((ItemAttackerRaiderMob)mob)) result = false;
		}
	}
}
