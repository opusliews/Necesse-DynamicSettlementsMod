package opusliews.patches;

import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.BehaviourTreeAI;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.composites.SequenceAINode;
import necesse.entity.mobs.ai.behaviourTree.trees.HumanAI;
import necesse.entity.mobs.ai.behaviourTree.leaves.ChaserAINode;
import necesse.entity.mobs.ai.behaviourTree.trees.ItemAttackerChaserAINode;
import necesse.entity.mobs.itemAttacker.ItemAttackSlot;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.inventory.InventoryItem;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.breaching.WarningBellSystem;
import opusliews.guard.GuardCombatKeys;
import opusliews.guard.GuardDutySystem;
import opusliews.guard.GuardFatigueSystem;
import opusliews.guard.GuardNeedsSystem;
import opusliews.guard.GuardLevelAssignmentSystem;
import opusliews.guard.NightGuardPatrolAINode;
import opusliews.guard.NightGuardTargetFinderAI;
import opusliews.guard.RestAwakenedGuardTargetFinderAI;
import opusliews.sleep.GuardWakePlayerAINode;
import opusliews.sleep.SleepWarningSystem;
import opusliews.multilevelsettlement.SettlementCrossLevelGuardCombatSystem;
import opusliews.logging.Logging;

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
		patrolCombat.addChild(new PatrolTargetActivityAINode());
		patrolCombat.addChild(new NightGuardCombatChaserAINode());
		root.addChildBefore(root.humanJobsFollowAINode, patrolCombat);

		SequenceAINode restAwakenedCombat = new SequenceAINode();
		restAwakenedCombat.addChild(new RestAwakenedCombatConditionAINode());
		restAwakenedCombat.addChild(new RestAwakenedGuardTargetFinderAI(640));
		restAwakenedCombat.addChild(new PatrolTargetActivityAINode());
		restAwakenedCombat.addChild(new ItemAttackerChaserAINode());
		root.addChildBefore(root.humanJobsFollowAINode, restAwakenedCombat);

		SequenceAINode crossLevelCombat = new SequenceAINode();
		crossLevelCombat.addChild(new CrossLevelGuardCombatAINode());
		crossLevelCombat.addChild(new ItemAttackerChaserAINode());
		root.addChildBefore(root.humanJobsFollowAINode, crossLevelCombat);

		root.addChildBefore(root.humanJobsFollowAINode, new GuardWakePlayerAINode());
		root.addChildBefore(root.humanJobsFollowAINode, new NightGuardPatrolAINode());
	}

	public static class NightGuardCombatChaserAINode extends ItemAttackerChaserAINode {
		public NightGuardCombatChaserAINode() {
			this.currentTargetKey = GuardCombatKeys.nightGuardTarget;
		}

		@Override
		public AINode getWeaponAI(ItemAttackerMob mob, ItemAttackSlot slot, InventoryItem weapon) {
			AINode child = super.getWeaponAI(mob, slot, weapon);
			if (child instanceof ChaserAINode) {
				ChaserAINode chaser = (ChaserAINode)child;
				chaser.targetKey = GuardCombatKeys.nightGuardTarget;
				chaser.chaserTargetKey = GuardCombatKeys.nightGuardChaserTarget;
			}
			return child;
		}
	}

	public static class CrossLevelGuardCombatAINode extends AINode {
		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			return SettlementCrossLevelGuardCombatSystem.tick((GuardHumanMob)mob, blackboard, this);
		}

		@Override
		public void onInterruptRunning(Mob mob, Blackboard blackboard) {
			SettlementCrossLevelGuardCombatSystem.clear((GuardHumanMob)mob, blackboard, "AI branch interrupted");
		}
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
			if (!guard.isSettlerOnCurrentLevel() || guard.hasCommandOrders() || !GuardLevelAssignmentSystem.isOnAssignedLevel(guard)) {
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
		private long nextDebugLogTime;
		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			GuardHumanMob guard = (GuardHumanMob)mob;
			boolean shouldPatrol = GuardDutySystem.shouldPatrol(guard);
			boolean onBreak = GuardNeedsSystem.isOnBreak(guard);
			boolean wakeAssignment = SleepWarningSystem.hasWakeAssignment(guard);
			AINodeResult result = shouldPatrol && !onBreak && !wakeAssignment ? AINodeResult.SUCCESS : AINodeResult.FAILURE;
			Mob currentTarget = (Mob)blackboard.getObject(Mob.class, GuardCombatKeys.nightGuardTarget);
			if (currentTarget != null && (currentTarget.removed() || currentTarget.getHealth() <= 0 || !currentTarget.isSamePlace(guard))) {
				blackboard.put(GuardCombatKeys.nightGuardTarget, null);
				currentTarget = null;
			}
			if (result == AINodeResult.FAILURE) {
				blackboard.put(GuardCombatKeys.nightGuardTarget, null);
			}
			else if (currentTarget == null) {
				result = AINodeResult.FAILURE;
			}
			long now = guard.getTime();
			if (Logging.logEnabled && currentTarget != null && now >= nextDebugLogTime) {
				nextDebugLogTime = now + 500L;
				Logging.logMessage("[GuardCombatDebug] Patrol condition guard=" + guard.getUniqueID()
						+ " result=" + result + " shouldPatrol=" + shouldPatrol + " onBreak=" + onBreak
						+ " wakeAssignment=" + wakeAssignment + " currentTarget=" + currentTarget.getStringID() + "#" + currentTarget.getUniqueID());
			}
			return result;
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
		private long nextDebugLogTime;
		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			GuardHumanMob guard = (GuardHumanMob)mob;
			Mob currentTarget = (Mob)blackboard.getObject(Mob.class, GuardCombatKeys.nightGuardTarget);
			Mob target = (Mob)blackboard.getObject(Mob.class, GuardCombatKeys.nightGuardChaserTarget);
			long now = guard.getTime();
			if (Logging.logEnabled && currentTarget != null && now >= nextDebugLogTime) {
				nextDebugLogTime = now + 500L;
				Logging.logMessage("[GuardCombatDebug] Patrol sequence reached pre-chaser guard=" + guard.getUniqueID()
						+ " currentTarget=" + currentTarget.getStringID() + "#" + currentTarget.getUniqueID()
						+ " chaserTarget=" + (target == null ? "null" : target.getStringID() + "#" + target.getUniqueID()));
			}
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
