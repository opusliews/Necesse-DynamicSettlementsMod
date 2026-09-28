package opusliews.multilevelsettlement;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINodeResult;
import necesse.entity.mobs.ai.behaviourTree.Blackboard;
import necesse.entity.mobs.ai.behaviourTree.CompositeTypedAINode;

/**
 * Compatibility shell for the old direct level-preference AI takeover.
 *
 * Level preference must never initiate movement on its own. SURFACE/CAVE are
 * soft weights used by SettlementCrossLevelJobSystem when choosing between
 * otherwise comparable work opportunities. Sleep, beds and ordinary idle AI
 * remain independent of this preference.
 */
public final class SettlementLevelPreferenceDirectAI {
	private SettlementLevelPreferenceDirectAI() {
	}

	public static AINodeResult tickIfActive(CompositeTypedAINode<?, ?> node, Mob mob, Blackboard<?> blackboard) {
		return null;
	}
}
