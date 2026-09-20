package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.CollisionChaserAINode;
import net.bytebuddy.asm.Advice;
import opusliews.tile.FireHazardPathing;

@ModMethodPatch(
		target = CollisionChaserAINode.class,
		name = "tickNode",
		arguments = {Mob.class, Blackboard.class}
)
public class FireHazardCollisionChaserDirectPathPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This CollisionChaserAINode node,
			@Advice.Argument(0) Mob mob,
			@Advice.Argument(1) Blackboard blackboard,
			@Advice.Local("fireOldDirectSearchDistance") int oldDirectSearchDistance,
			@Advice.Local("fireOldStoppingDistance") int oldStoppingDistance,
			@Advice.Local("fireChangedDirectSearch") boolean changed
	) {
		oldDirectSearchDistance = node.directSearchDistance;
		oldStoppingDistance = node.stoppingDistance;
		changed = false;

		Mob target = (Mob)blackboard.getObject(Mob.class, node.targetKey);
		if (target == null || !FireHazardPathing.directMovementCrossesFire(mob, target.getX(), target.getY())) return;

		if (blackboard.mover.isCurrentlyMovingFor(node) && blackboard.mover.hasMobTarget()) {
			blackboard.mover.stopMoving(mob);
		}

		node.nextPathFindTime = 0L;
		node.directSearchDistance = -1;
		node.stoppingDistance = -1;
		changed = true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This CollisionChaserAINode node,
			@Advice.Local("fireOldDirectSearchDistance") int oldDirectSearchDistance,
			@Advice.Local("fireOldStoppingDistance") int oldStoppingDistance,
			@Advice.Local("fireChangedDirectSearch") boolean changed
	) {
		if (!changed) return;
		node.directSearchDistance = oldDirectSearchDistance;
		node.stoppingDistance = oldStoppingDistance;
	}
}
