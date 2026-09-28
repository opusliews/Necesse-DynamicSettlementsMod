package opusliews.multilevelsettlement;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.decorators.MoveTaskAINode;

/**
 * Compatibility shell for the old preferred-level movement node.
 *
 * Preferences no longer cause autonomous relocation. They are consumed only as
 * soft weights by SettlementCrossLevelJobSystem when a real job choice exists.
 */
public class SettlementLevelPreferenceAINode extends MoveTaskAINode {
	public enum Mode {
		RECREATION,
		IDLE
	}

	public SettlementLevelPreferenceAINode(Mode mode) {
	}

	@Override
	protected void onRootSet(necesse.entity.mobs.ai.behaviourTree.AINode root, Mob mob, Blackboard blackboard) {
	}

	@Override
	public void init(Mob mob, Blackboard blackboard) {
	}

	@Override
	public AINodeResult tickNode(Mob mob, Blackboard blackboard) {
		return AINodeResult.FAILURE;
	}

	@Override
	public AINodeResult onTaskFailed(Mob mob, Blackboard blackboard) {
		return AINodeResult.FAILURE;
	}
}
