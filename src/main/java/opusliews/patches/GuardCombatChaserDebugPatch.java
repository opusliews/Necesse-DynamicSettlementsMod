package opusliews.patches;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.trees.ItemAttackerChaserAINode;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardCombatKeys;
import opusliews.logging.Logging;

@ModMethodPatch(target = ItemAttackerChaserAINode.class, name = "tickChild", arguments = {AINode.class, ItemAttackerMob.class, Blackboard.class})
public class GuardCombatChaserDebugPatch {
	private static final Map<GuardHumanMob, Long> nextLogTimes = Collections.synchronizedMap(new WeakHashMap<>());

	public static boolean shouldLog(GuardHumanMob guard) {
		long now = guard.getTime();
		Long next = nextLogTimes.get(guard);
		if (next != null && now < next) return false;
		nextLogTimes.put(guard, now + 500L);
		return true;
	}

	@Advice.OnMethodEnter
	public static void onEnter(@Advice.Argument(0) AINode child,
			@Advice.Argument(1) ItemAttackerMob mob,
			@Advice.Argument(2) Blackboard<?> blackboard) {
		if (!Logging.logEnabled || !(mob instanceof GuardHumanMob)) return;
		GuardHumanMob guard = (GuardHumanMob)mob;
		Mob nightTarget = (Mob)blackboard.getObject(Mob.class, GuardCombatKeys.nightGuardTarget);
		Mob currentTarget = nightTarget != null ? nightTarget : (Mob)blackboard.getObject(Mob.class, "currentTarget");
		if (currentTarget == null) return;
		if (!shouldLog(guard)) return;
		Mob chaserTarget = (Mob)blackboard.getObject(Mob.class, nightTarget != null ? GuardCombatKeys.nightGuardChaserTarget : "chaserTarget");
		Logging.logMessage("[GuardCombatDebug] Chaser ENTER guard=" + guard.getUniqueID()
				+ " currentTarget=" + currentTarget.getStringID() + "#" + currentTarget.getUniqueID()
				+ " chaserTarget=" + (chaserTarget == null ? "null" : chaserTarget.getStringID() + "#" + chaserTarget.getUniqueID())
				+ " child=" + (child == null ? "null" : child.getClass().getName()));
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(1) ItemAttackerMob mob,
			@Advice.Argument(2) Blackboard<?> blackboard,
			@Advice.Return AINodeResult result) {
		if (!Logging.logEnabled || !(mob instanceof GuardHumanMob)) return;
		GuardHumanMob guard = (GuardHumanMob)mob;
		Mob nightTarget = (Mob)blackboard.getObject(Mob.class, GuardCombatKeys.nightGuardTarget);
		Mob currentTarget = nightTarget != null ? nightTarget : (Mob)blackboard.getObject(Mob.class, "currentTarget");
		if (currentTarget == null) return;
		Mob chaserTarget = (Mob)blackboard.getObject(Mob.class, nightTarget != null ? GuardCombatKeys.nightGuardChaserTarget : "chaserTarget");
		Logging.logMessage("[GuardCombatDebug] Chaser EXIT guard=" + guard.getUniqueID()
				+ " result=" + result
				+ " currentTarget=" + currentTarget.getStringID() + "#" + currentTarget.getUniqueID()
				+ " chaserTarget=" + (chaserTarget == null ? "null" : chaserTarget.getStringID() + "#" + chaserTarget.getUniqueID()));
	}
}
