package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanCommandMoveToAINode;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementCrossLevelCommandSystem;

@ModMethodPatch(target = HumanCommandMoveToAINode.class, name = "onArrived", arguments = {HumanMob.class})
public class CrossLevelCommandGuardArrivedPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(0) HumanMob human) {
		return SettlementCrossLevelCommandSystem.onCrossLevelGuardArrived(human);
	}
}
