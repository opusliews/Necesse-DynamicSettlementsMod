package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardDutySystem;

@ModMethodPatch(target = HumanMob.class, name = "tickRecreationUsage", arguments = {})
public class GuardDutyRecreationUsagePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This HumanMob mob) {
		if (!(mob instanceof GuardHumanMob)) {
			return false;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!GuardDutySystem.usesNightSchedule(guard)) {
			return false;
		}

		if (!guard.isOnStrike() && guard.getCurrentMission() == null && !guard.adventureParty.isInAdventureParty()) {
			float usageModifier = 1.0F;
			if (guard.isBeingInteractedWith()) {
				usageModifier *= 0.25F;
			}

			if (!guard.getWorldEntity().isNight()) {
				usageModifier *= 0.25F;
				if (guard.getRecreationLevel() <= 0.05F) {
					usageModifier = 0.0F;
				}
			}

			if (guard.getRecreationLevel() < 0.3F) {
				usageModifier *= 0.5F;
			}
			if (guard.getRecreationLevel() < 0.15F) {
				usageModifier *= 0.5F;
			}

			guard.useRecreation(50.0F, usageModifier);
			if (guard.isServer() && !guard.isOnStrike() && guard.getWorldEntity().isNight()) {
				guard.recreationStrikeCooldownBuffer -= 50;
				if (guard.recreationStrikeCooldownBuffer <= 0) {
					guard.attemptStartStrike(false);
					guard.resetRecreationStrikeCooldownBuffer();
				}
			}

			if (guard.getRecreationLevel() >= 0.3F) {
				guard.resetRecreationStrikeCooldownBuffer();
			}
		}

		return true;
	}
}
