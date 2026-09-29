package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.CommandAttackTargetterAINode;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = CommandAttackTargetterAINode.class, name = "tick", arguments = {Mob.class, Blackboard.class})
public class CrossLevelCommandAttackTargetterPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This CommandAttackTargetterAINode node,
			@Advice.Argument(0) Mob mob,
			@Advice.Argument(1) Blackboard blackboard) {
		if (!(mob instanceof HumanMob)) return false;
		if (!SettlementCrossLevelCommandSystem.shouldSuppressVanillaAttackTargeting((HumanMob)mob)) return false;
		Mob current = (Mob)blackboard.getObject(Mob.class, node.currentTargetKey);
		if (current == ((HumanMob)mob).commandAttackMob) blackboard.put(node.currentTargetKey, null);
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Enter boolean handled, @Advice.Return(readOnly = false) AINodeResult result) {
		if (handled) result = AINodeResult.FAILURE;
	}
}
