package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardCombatKeys;
import opusliews.guard.GuardDutySystem;
import opusliews.guard.GuardFatigueSystem;
import opusliews.guard.GuardLevelAssignmentSystem;
import opusliews.logging.Logging;

@ModMethodPatch(target = Mob.class, name = "isServerHit", arguments = {GameDamage.class, float.class, float.class, float.class, Attacker.class})
public class GuardFatigueCombatPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This Mob mob, @Advice.Argument(4) Attacker attacker) {
		if (!(mob instanceof GuardHumanMob) || attacker == null) {
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		Mob attackOwner = attacker.getAttackOwner();
		if (attackOwner != null && attackOwner != mob) {
			Logging.logMessage("GuardFatigueDebug: isServerHit guard=" + guard.getUniqueID()
					+ " attacker=" + attackOwner.getStringID() + "#" + attackOwner.getUniqueID()
					+ " scheduledRest=" + GuardFatigueSystem.isScheduledRestPeriod(guard));
			GuardFatigueSystem.registerDirectRestAttacker(guard, attackOwner);

			if (GuardDutySystem.shouldPatrol(guard)
					&& GuardLevelAssignmentSystem.isOnAssignedLevel(guard)
					&& attackOwner.isSamePlace(guard)
					&& !attackOwner.removed()
					&& attackOwner.getHealth() > 0
					&& attackOwner.canTakeDamage()
					&& attackOwner.canBeHit(guard)
					&& attackOwner.canBeTargetedByHumans(guard)
					&& guard.ai != null) {
				guard.ai.blackboard.put(GuardCombatKeys.nightGuardChaserTarget, null);
				guard.ai.blackboard.put(GuardCombatKeys.nightGuardTarget, attackOwner);
				guard.ai.blackboard.submitEvent("resetPathTime", new necesse.entity.mobs.ai.behaviourTree.event.AIEvent());
				if (Logging.logEnabled) Logging.logMessage("NightGuard: guard " + guard.getUniqueID()
						+ " forced attacker as combat target=" + attackOwner.getStringID() + "#" + attackOwner.getUniqueID());
			}
		}
	}
}
