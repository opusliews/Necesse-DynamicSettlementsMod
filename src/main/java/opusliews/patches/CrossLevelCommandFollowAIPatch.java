package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanCommandFollowMobAINode;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = HumanCommandFollowMobAINode.class, name = "tickFollowing", arguments = {Mob.class, HumanMob.class, Blackboard.class})
public class CrossLevelCommandFollowAIPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This HumanCommandFollowMobAINode node,
			@Advice.Argument(0) Mob target,
			@Advice.Argument(1) HumanMob human,
			@Advice.Argument(2) Blackboard<?> blackboard,
			@Advice.Local("crossLevelResult") AINodeResult crossLevelResult) {
		crossLevelResult = SettlementCrossLevelCommandSystem.tickCrossLevelFollow(node, target, human, blackboard);
		return crossLevelResult != null;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Enter boolean handled,
			@Advice.Local("crossLevelResult") AINodeResult crossLevelResult,
			@Advice.Return(readOnly = false) AINodeResult result) {
		if (handled) result = crossLevelResult;
	}
}
