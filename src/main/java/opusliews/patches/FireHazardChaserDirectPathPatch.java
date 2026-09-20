package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.leaves.ChaserAINode;
import net.bytebuddy.asm.Advice;
import opusliews.tile.FireHazardPathing;

@ModMethodPatch(
		target = ChaserAINode.class,
		name = "getDirectChaseDirection",
		arguments = {Mob.class, Mob.class, float.class, boolean.class}
)
public class FireHazardChaserDirectPathPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) Mob mob,
			@Advice.Argument(1) Mob target,
			@Advice.Return(readOnly = false) ChaserAINode.ChaseDirection result
	) {
		if (result != ChaserAINode.ChaseDirection.INVALID
				&& FireHazardPathing.directMovementCrossesFire(mob, target.getX(), target.getY())) {
			result = ChaserAINode.ChaseDirection.INVALID;
		}
	}
}
