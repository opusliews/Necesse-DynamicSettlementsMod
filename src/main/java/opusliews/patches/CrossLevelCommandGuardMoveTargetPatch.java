package opusliews.patches;

import java.awt.Point;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanCommandMoveToAINode;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = HumanCommandMoveToAINode.class, name = "getLevelPosition", arguments = {HumanMob.class})
public class CrossLevelCommandGuardMoveTargetPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) HumanMob human, @Advice.Return(readOnly = false) Point result) {
		Point crossLevelTarget = SettlementCrossLevelCommandSystem.getCrossLevelGuardMoveTarget(human);
		if (crossLevelTarget != null) result = crossLevelTarget;
	}
}
