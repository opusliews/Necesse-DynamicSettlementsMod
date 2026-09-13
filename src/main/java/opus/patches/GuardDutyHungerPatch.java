package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardDutySystem;
import opus.guard.GuardNeedsSystem;

@ModMethodPatch(target = HumanMob.class, name = "tickHunger", arguments = {})
public class GuardDutyHungerPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This HumanMob mob) {
		if (!(mob instanceof GuardHumanMob)) {
			return false;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!GuardDutySystem.usesNightSchedule(guard)) {
			return false;
		}

		GuardNeedsSystem.logNightShiftIfNeeded(guard);

		if (guard.getCurrentMission() == null && guard.getWorldEntity().isNight()) {
			float usageModifier = guard.isBeingInteractedWith() ? 0.25F : 1.0F;
			float usedHunger = (float)(50.0 / (1000.0 * (double)HumanMob.secondsToPassAtFullHunger)) * usageModifier;
			guard.useHunger(usedHunger, false);

			if (guard.hungerLevel <= 0.0F && guard.isServer()) {
				guard.hungerStrikeCooldownBuffer -= 50;
				if (guard.hungerStrikeCooldownBuffer <= 0) {
					if (guard.isOnStrike()) {
						guard.addLeaveBuffer(0.2F);
					}
					else {
						guard.attemptStartStrike(false);
					}
					guard.resetHungerStrikeCooldownBuffer();
				}
			}

			if (guard.hungerLevel < 0.1F) {
				guard.lastFoodEaten = null;
			}
			else {
				guard.resetHungerStrikeCooldownBuffer();
			}
		}

		return true;
	}
}
