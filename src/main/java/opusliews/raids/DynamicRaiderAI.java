package opusliews.raids;

import java.awt.Point;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.RaiderMobPhase;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.CompositeAINode;
import necesse.entity.mobs.ai.behaviourTree.BehaviourTreeAI;
import necesse.entity.mobs.ai.behaviourTree.leaves.ChaserAINode;
import necesse.entity.mobs.ai.behaviourTree.leaves.EscapeAINode;
import necesse.entity.mobs.ai.behaviourTree.leaves.MoveToAINode;
import necesse.entity.mobs.ai.behaviourTree.leaves.WandererAINode;
import necesse.entity.mobs.ai.behaviourTree.trees.ItemAttackerPlayerChaserAI;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import necesse.entity.mobs.ai.behaviourTree.util.WandererBaseOptions;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import necesse.inventory.InventoryItem;

public class DynamicRaiderAI extends CompositeAINode {
	private final EscapeAINode escapeAI;
	private final ItemAttackerPlayerChaserAI combatAI;
	private final MoveToAINode strategicMoveAI;
	private final MoveToAINode caveEscapeMoveAI;
	private final WandererAINode wanderAI;

	public DynamicRaiderAI() {
		escapeAI = new EscapeAINode() {
			@Override
			public boolean shouldEscape(Mob mob, Blackboard blackboard) {
				return mob instanceof ItemAttackerRaiderMob && MultiLevelRaidSystem.shouldRetreat((ItemAttackerRaiderMob)mob);
			}
		};
		addChild(escapeAI);

		combatAI = new ItemAttackerPlayerChaserAI(192, new InventoryItem("woodsword")) {
			@Override
			public void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
				super.onRootSet(root, mob, blackboard);
				ItemAttackerRaiderMob raider = (ItemAttackerRaiderMob)mob;
				blackboard.onEvent("itemAttackerUpdated", e -> {
					ChaserAINode chaser = itemAttackerChaserAINode.getChaserAIIfExists();
					if (chaser != null) chaser.moveIfFailedPath = (target, path) -> target instanceof Mob && raider.getDistance((Mob)target) > 160.0F;
				});
			}
		};
		combatAI.targetFinderAINode.loseTargetMinCooldown = 500;
		combatAI.targetFinderAINode.loseTargetMaxCooldown = 1000;
		addChild(combatAI);

		strategicMoveAI = new MoveToAINode() {
			@Override
			public AINodeResult tickNode(Mob mob, Blackboard blackboard) {
				ItemAttackerRaiderMob raider = (ItemAttackerRaiderMob)mob;
				moveToTile = MultiLevelRaidSystem.getStrategicMoveToTile(raider);
				if (moveToTile == null) return AINodeResult.FAILURE;
				return super.tickNode(mob, blackboard);
			}
		};
		addChild(strategicMoveAI);

		caveEscapeMoveAI = new MoveToAINode(null) {
			@Override
			public AINodeResult tickNode(Mob mob, Blackboard blackboard) {
				ItemAttackerRaiderMob raider = (ItemAttackerRaiderMob)mob;
				moveToTile = MultiLevelRaidSystem.getCaveEscapeMoveToTile(raider);
				if (moveToTile == null) return AINodeResult.FAILURE;
				return super.tickNode(mob, blackboard);
			}
		};
		addChild(caveEscapeMoveAI);

		wanderAI = new WandererAINode(4000) {
			@Override
			public WandererBaseOptions getBaseOptions() {
				return new WandererBaseOptions() {
					@Override
					public Point getBaseTile(Mob mob) {
						ItemAttackerRaiderMob raider = (ItemAttackerRaiderMob)mob;
						Point waiting = MultiLevelRaidSystem.getWaitingBaseTile(raider);
						return waiting != null ? waiting : raider.phase == RaiderMobPhase.PREPARING ? raider.preparingTile : null;
					}
				};
			}
		};
		wanderAI.searchRadius = 5;
		wanderAI.runAwayFromAttacker = false;
		addChild(wanderAI);
	}

	public static BehaviourTreeAI create(ItemAttackerRaiderMob raider) {
		return new BehaviourTreeAI(raider, new DynamicRaiderAI(), new AIMover(HumanMob.humanPathIterations));
	}

	@Override
	public void init(Mob mob, Blackboard blackboard) {
	}

	@Override
	public AINodeResult tick(Mob mob, Blackboard blackboard) {
		ItemAttackerRaiderMob raider = (ItemAttackerRaiderMob)mob;
		tickPreparing(raider, blackboard);

		if (MultiLevelRaidSystem.shouldRetreat(raider)) {
			MultiLevelRaidSystem.prepareRetreatState(raider);

			caveEscapeMoveAI.init(raider, blackboard);
			AINodeResult caveEscapeResult = caveEscapeMoveAI.lastResult = caveEscapeMoveAI.tick(raider, blackboard);
			if (caveEscapeResult == AINodeResult.RUNNING || caveEscapeResult == AINodeResult.SUCCESS) return caveEscapeResult;

			escapeAI.init(raider, blackboard);
			return escapeAI.lastResult = escapeAI.tick(raider, blackboard);
		}

		if (raider.phase == RaiderMobPhase.PREPARING) {
			wanderAI.init(raider, blackboard);
			return wanderAI.lastResult = wanderAI.tick(raider, blackboard);
		}

		MultiLevelRaidSystem.tryLootAtGoal(raider);

		if (MultiLevelRaidSystem.shouldUseStrategicCombat(raider)) {
			combatAI.init(raider, blackboard);
			AINodeResult combatResult = combatAI.lastResult = combatAI.tick(raider, blackboard);
			if (combatResult == AINodeResult.RUNNING || combatResult == AINodeResult.SUCCESS) return combatResult;
		}

		strategicMoveAI.init(raider, blackboard);
		AINodeResult moveResult = strategicMoveAI.lastResult = strategicMoveAI.tick(raider, blackboard);
		if (moveResult == AINodeResult.RUNNING || moveResult == AINodeResult.SUCCESS) return moveResult;

		wanderAI.init(raider, blackboard);
		return wanderAI.lastResult = wanderAI.tick(raider, blackboard);
	}

	private static void tickPreparing(ItemAttackerRaiderMob mob, Blackboard blackboard) {
		if (mob.phase != RaiderMobPhase.PREPARING) return;
		if (mob.raidingStartTimer > 0) mob.raidingStartTimer -= 50;
		if (mob.raidingStartTimer <= 0) startRaid(mob);

	}

	private static void startRaid(ItemAttackerRaiderMob mob) {
		mob.phase = RaiderMobPhase.RAIDING;
		SettlementRaidLevelEvent raidEvent = mob.getRaidEvent();
		if (raidEvent != null) raidEvent.startRaid(true);
	}

	@Override
	protected AINodeResult tickChildren(AINode lastRunningChild, AINodeResult runningChildResult, Iterable children, Mob mob, Blackboard blackboard) {
		return AINodeResult.FAILURE;
	}
}
