package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanJobSearchingAINode;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.SettlerStarvationSystem;

@ModMethodPatch(
		target = HumanJobSearchingAINode.class,
		name = "tick",
		arguments = {HumanMob.class, Blackboard.class}
)
public class EmergencyFoodJobSearchPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This HumanJobSearchingAINode node,
			@Advice.Argument(0) HumanMob human
	) {
		if (SettlerStarvationSystem.isEmergencyEating(human)) {
			node.nextSearchTime = 0L;
		}
	}
}
