package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanSleepAINode;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelSleepSystem;

@ModMethodPatch(target = HumanSleepAINode.class, name = "tickNode", arguments = {HumanMob.class, Blackboard.class})
public class HumanSleepCrossLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This HumanSleepAINode node,
			@Advice.Argument(0) HumanMob human,
			@Advice.Argument(1) Blackboard<?> blackboard,
			@Advice.Local("dsHandled") boolean handled,
			@Advice.Local("dsResult") AINodeResult result) {
		result = SettlementCrossLevelSleepSystem.tickCrossLevelSleep(node, human, blackboard);
		handled = result != null;
		return handled;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Local("dsHandled") boolean handled,
			@Advice.Local("dsResult") AINodeResult result,
			@Advice.Return(readOnly = false) AINodeResult returned) {
		if (handled) returned = result;
	}
}
