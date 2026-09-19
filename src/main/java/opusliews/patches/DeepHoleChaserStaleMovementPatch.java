package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.ChaserAINode;
import net.bytebuddy.asm.Advice;
import opusliews.deephole.DeepHolePathing;

@ModMethodPatch(
		target = ChaserAINode.class,
		name = "tickNode",
		arguments = {Mob.class, Blackboard.class}
)
public class DeepHoleChaserStaleMovementPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This ChaserAINode node,
			@Advice.Argument(0) Mob mob,
			@Advice.Argument(1) Blackboard blackboard
	) {
		if (!blackboard.mover.isCurrentlyMovingFor(node) || !blackboard.mover.hasMobTarget()) return;

		Mob target = blackboard.mover.getTargetMob();
		if (target == null || !DeepHolePathing.directMovementCrossesDeepHole(mob, target.getX(), target.getY())) return;

		blackboard.mover.stopMoving(mob);
		node.nextPathFindTime = 0L;
	}
}
