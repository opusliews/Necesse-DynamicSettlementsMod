package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.leaves.MoveToAINode;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = MoveToAINode.class, name = "tickNode", arguments = {Mob.class, Blackboard.class})
public class RaiderCaveMoveCooldownPatch {
	@Advice.OnMethodEnter
	static void onEnter(@Advice.This MoveToAINode node, @Advice.Argument(0) Mob mob, @Advice.Argument(1) Blackboard blackboard) {
		MultiLevelRaidSystem.prepareCaveMoveTick(node, mob, blackboard);
	}
}
