package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.ComputedValue;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.trees.HumanTargetFinderAI;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardFatigueSystem;
import opus.logging.Logging;

import java.util.HashMap;
import java.util.Map;

@ModMethodPatch(
		target = HumanTargetFinderAI.class,
		name = "isValidTarget",
		arguments = {HumanMob.class, Mob.class, boolean.class, boolean.class, ComputedValue.class, boolean.class}
)
public class GuardDutyTargetFinderRestPatch {
	private static final Map<Long, Long> nextTargetLogs = new HashMap<>();

	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(0) HumanMob mob,
			@Advice.Argument(1) Mob target
	) {
		if (!(mob instanceof GuardHumanMob)) {
			return false;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!GuardFatigueSystem.isScheduledRestPeriod(guard)) {
			return false;
		}

		logTargetCheck(guard, target, GuardFatigueSystem.isRestAwakenedByCombat(guard)
				? "base vanilla finder blocked during rest; dedicated awakened-combat finder handles targets"
				: "sleeping: base vanilla target blocked");
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Enter boolean blocked, @Advice.Return(readOnly = false) boolean result) {
		if (blocked) {
			result = false;
		}
	}

	public static void logTargetCheck(GuardHumanMob guard, Mob target, String message) {
		if (guard == null || target == null || guard.getLevel() == null) {
			return;
		}

		long now = guard.getLevel().getTime();
		long key = ((long)guard.getUniqueID() << 32) ^ (target.getUniqueID() & 0xffffffffL);
		synchronized (nextTargetLogs) {
			long next = nextTargetLogs.getOrDefault(key, 0L);
			if (now < next) {
				return;
			}
			nextTargetLogs.put(key, now + 500L);
		}

		Logging.logMessage("GuardFatigueDebug: target finder guard=" + guard.getUniqueID()
				+ " target=" + target.getStringID() + "#" + target.getUniqueID()
				+ " " + message);
	}
}
