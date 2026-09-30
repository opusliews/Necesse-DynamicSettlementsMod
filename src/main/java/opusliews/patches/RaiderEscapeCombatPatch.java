package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.EscapeAINode;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = EscapeAINode.class, name = "tickNode", arguments = {Mob.class, Blackboard.class})
public class RaiderEscapeCombatPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.Argument(0) Mob mob, @Advice.Return AINodeResult result) {
		if (result != AINodeResult.FAILURE && mob instanceof ItemAttackerRaiderMob) MultiLevelRaidSystem.tryAttackWhileEscaping((ItemAttackerRaiderMob)mob);
	}
}
