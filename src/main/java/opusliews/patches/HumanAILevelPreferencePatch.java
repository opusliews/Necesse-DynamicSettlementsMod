package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.CompositeTypedAINode;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelPreferenceDirectAI;

@ModMethodPatch(target = CompositeTypedAINode.class, name = "tick", arguments = {Mob.class, Blackboard.class})
public class HumanAILevelPreferencePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This CompositeTypedAINode<?, ?> node,
			@Advice.Argument(0) Mob mob,
			@Advice.Argument(1) Blackboard<?> blackboard,
			@Advice.Local("dsHandled") boolean handled,
			@Advice.Local("dsResult") AINodeResult result
	) {
		result = SettlementLevelPreferenceDirectAI.tickIfActive(node, mob, blackboard);
		handled = result != null;
		return handled;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Local("dsHandled") boolean handled,
			@Advice.Local("dsResult") AINodeResult result,
			@Advice.Return(readOnly = false) AINodeResult returned
	) {
		if (handled) returned = result;
	}
}
