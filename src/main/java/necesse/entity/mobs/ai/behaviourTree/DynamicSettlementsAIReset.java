package necesse.entity.mobs.ai.behaviourTree;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.decorators.TaskAINode;

public final class DynamicSettlementsAIReset {
	private DynamicSettlementsAIReset() {
	}

	public static void reset(AINode<?> tree, Mob mob, Blackboard<?> blackboard) {
		if (tree == null || mob == null || blackboard == null) return;
		clearTasks(tree);
		resetRunning(tree, mob, blackboard);
	}

	private static void clearTasks(AINode<?> node) {
		if (node instanceof TaskAINode<?>) ((TaskAINode<?>)node).clearTask();
		for (AINode<?> child : node.debugChildren()) clearTasks(child);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void resetRunning(AINode<?> tree, Mob mob, Blackboard<?> blackboard) {
		((AINode)tree).onInterruptRunning(mob, (Blackboard)blackboard);
	}
}
