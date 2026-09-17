package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.SummonTargetFinderAINode;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import net.bytebuddy.asm.Advice;
import opusliews.trapdoor.TrapdoorSystem;

@ModMethodPatch(target = SummonTargetFinderAINode.class, name = "tickNode", arguments = {Mob.class, Blackboard.class})
public class TrapdoorSummonAITargetPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This SummonTargetFinderAINode node,
			@Advice.Argument(0) Mob mob,
			@Advice.Argument(1) Blackboard blackboard
	) {
		ItemAttackerMob owner = mob.getFollowingItemAttacker();
		if (!(owner instanceof PlayerMob) || !TrapdoorSystem.isHidden((PlayerMob)owner)) return false;

		blackboard.put(node.currentTargetKey, null);
		blackboard.put("focusTarget", null);
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean blocked,
			@Advice.Return(readOnly = false) AINodeResult result
	) {
		if (blocked) result = AINodeResult.FAILURE;
	}
}
