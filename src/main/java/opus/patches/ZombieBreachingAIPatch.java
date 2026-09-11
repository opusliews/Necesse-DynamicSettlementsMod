package opus.patches;

import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.BehaviourTreeAI;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.decorators.MoveTaskAINode;
import necesse.entity.mobs.ai.behaviourTree.trees.ConfusedCollisionPlayerChaserWandererAI;
import necesse.entity.mobs.ai.behaviourTree.trees.ConfusedPlayerChaserWandererAI;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import necesse.entity.mobs.ai.behaviourTree.util.MoveToTileAITask;
import net.bytebuddy.asm.Advice;
import opus.breaching.ZombieBreaching;
import opus.damage.HardcoreFeatures;

import java.awt.*;

@ModConstructorPatch(target = BehaviourTreeAI.class, arguments = {Mob.class, AINode.class, AIMover.class})
public class ZombieBreachingAIPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) Mob mob, @Advice.Argument(1) AINode tree) {
		if (!HardcoreFeatures.isServerEnabled() || !ZombieBreaching.isZombie(mob)) {
			return;
		}

		ZombieDoorBreacherAINode node = new ZombieDoorBreacherAINode();
		if (tree instanceof ConfusedCollisionPlayerChaserWandererAI) {
			ConfusedCollisionPlayerChaserWandererAI root = (ConfusedCollisionPlayerChaserWandererAI)tree;
			root.addChildBefore(root.wandererAINode, node);
		}
		else if (tree instanceof ConfusedPlayerChaserWandererAI) {
			ConfusedPlayerChaserWandererAI root = (ConfusedPlayerChaserWandererAI)tree;
			root.addChildBefore(root.wandererAINode, node);
		}
	}

	public static class ZombieDoorBreacherAINode extends MoveTaskAINode {
		public ZombieDoorBreacherAINode() {
		}
		private Point movingToDoor;

		@Override
		protected void onRootSet(AINode root, Mob mob, Blackboard blackboard) {
		}

		@Override
		public void init(Mob mob, Blackboard blackboard) {
		}

		@Override
		public AINodeResult tick(Mob mob, Blackboard blackboard) {
			// Explicit priority guard. This keeps player > passive animal > door even
			// if constructor-patch ordering places this node before the passive chaser.
			if (blackboard.getObject(Mob.class, "currentTarget") != null
					|| blackboard.getObject(Mob.class, ZombieBreaching.passiveTargetKey) != null) {
				stop(mob, blackboard);
				return AINodeResult.FAILURE;
			}

			Point door = ZombieBreaching.getState(mob).rememberedDoor;
			if (door == null) {
				stop(mob, blackboard);
				return AINodeResult.FAILURE;
			}

			if (movingToDoor == null || !movingToDoor.equals(door)) {
				clearTask();
				movingToDoor = new Point(door);
			}

			return super.tick(mob, blackboard);
		}

		@Override
		public AINodeResult tickNode(Mob mob, Blackboard blackboard) {
			if (movingToDoor == null) {
				return AINodeResult.FAILURE;
			}

			if (blackboard.mover.isCurrentlyMovingFor(this)) {
				return AINodeResult.RUNNING;
			}

			return moveToTileTask(movingToDoor.x, movingToDoor.y, null, pathObject -> {
				MoveToTileAITask.AIPathResult path = (MoveToTileAITask.AIPathResult)pathObject;
				path.moveIfWithin(-1, -1, null);
				return AINodeResult.RUNNING;
			});
		}

		@Override
		public AINodeResult onTaskFailed(Mob mob, Blackboard blackboard) {
			return ZombieBreaching.getState(mob).rememberedDoor != null ? AINodeResult.RUNNING : AINodeResult.FAILURE;
		}

		private void stop(Mob mob, Blackboard blackboard) {
			clearTask();
			if (blackboard.mover.isCurrentlyMovingFor(this)) {
				blackboard.mover.stopMoving(mob);
			}
			movingToDoor = null;
		}
	}
}
