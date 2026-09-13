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
import opus.guard.GuardDutySystem;
import opus.guard.GuardFatigueSystem;
import opus.guard.GuardNeedsSystem;
import opus.guard.NightGuardPatrolAINode;
import opus.guard.NightGuardTargetFinderAI;
import opus.guard.RestAwakenedGuardTargetFinderAI;
import opus.sleep.GuardWakePlayerAINode;
import opus.sleep.SleepWarningSystem;

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

		SequenceAINode patrolCombat = new SequenceAINode();
		patrolCombat.addChild(new PatrolCombatConditionAINode());
		patrolCombat.addChild(new NightGuardTargetFinderAI(640));
		patrolCombat.addChild(new PatrolTargetActivityAINode());
		patrolCombat.addChild(new ItemAttackerChaserAINode());
		root.addChildBefore(root.humanJobsFollowAINode, patrolCombat);

		SequenceAINode restAwakenedCombat = new SequenceAINode();
		restAwakenedCombat.addChild(new RestAwakenedCombatConditionAINode());
		restAwakenedCombat.addChild(new RestAwakenedGuardTargetFinderAI(640));
		restAwakenedCombat.addChild(new PatrolTargetActivityAINode());
		restAwakenedCombat.addChild(new ItemAttackerChaserAINode());
		root.addChildBefore(root.humanJobsFollowAINode, restAwakenedCombat);

		root.addChildBefore(root.humanJobsFollowAINode, new GuardWakePlayerAINode());
		root.addChildBefore(root.humanJobsFollowAINode, new NightGuardPatrolAINode());
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

			blackboard.put("currentTarget", target);
			return AINodeResult.SUCCESS;
		}
	}

	public static class PatrolCombatConditionAINode extends AINode {
		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			GuardHumanMob guard = (GuardHumanMob)mob;
			return GuardDutySystem.shouldPatrol(guard)
					&& !GuardNeedsSystem.isOnBreak(guard)
					&& !SleepWarningSystem.hasWakeAssignment(guard)
					? AINodeResult.SUCCESS
					: AINodeResult.FAILURE;
		}
	}

	public static class RestAwakenedCombatConditionAINode extends AINode {
		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			GuardHumanMob guard = (GuardHumanMob)mob;
			return GuardFatigueSystem.isRestAwakenedByCombat(guard)
					&& !SleepWarningSystem.hasWakeAssignment(guard)
					? AINodeResult.SUCCESS
					: AINodeResult.FAILURE;
		}
	}

	public static class PatrolTargetActivityAINode extends AINode {
		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			Mob target = (Mob)blackboard.getObject(Mob.class, "chaserTarget");
			if (target != null) {
				((GuardHumanMob)mob).setActivity(
						"chaser",
						20000,
						new necesse.engine.localization.message.LocalMessage(
								"activities", "attacking", "target", target.getLocalization()
						)
				);
			}
			return AINodeResult.SUCCESS;
		}
	}
}
