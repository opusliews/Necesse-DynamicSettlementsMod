package opus.patches;

import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.BehaviourTreeAI;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.composites.SequenceAINode;
import necesse.entity.mobs.ai.behaviourTree.trees.HumanAI;
import necesse.entity.mobs.ai.behaviourTree.trees.ItemAttackerChaserAINode;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import net.bytebuddy.asm.Advice;
import opus.breaching.WarningBellSystem;

@ModConstructorPatch(target = BehaviourTreeAI.class, arguments = {Mob.class, AINode.class, AIMover.class})
public class WarningBellGuardAIPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) Mob mob, @Advice.Argument(1) AINode tree) {
		if (!(mob instanceof GuardHumanMob) || !(tree instanceof HumanAI)) {
			return;
		}

		HumanAI root = (HumanAI)tree;
		SequenceAINode warningBellCombat = new SequenceAINode();
		warningBellCombat.addChild(new WarningBellTargetAINode());
		warningBellCombat.addChild(new ItemAttackerChaserAINode());
		root.addChildBefore(root.humanJobsFollowAINode, warningBellCombat);
	}

	public static class WarningBellTargetAINode extends AINode {
		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			GuardHumanMob guard = (GuardHumanMob)mob;
			if (!guard.isSettlerOnCurrentLevel() || guard.hasCommandOrders()) {
				return AINodeResult.FAILURE;
			}

			Mob target = WarningBellSystem.getGuardTargetMob(guard);
			if (target == null || target.removed() || target.getHealth() <= 0 || !target.isSamePlace(guard)) {
				return AINodeResult.FAILURE;
			}

			blackboard.put("chaserTarget", target);
			return AINodeResult.SUCCESS;
		}
	}
}
