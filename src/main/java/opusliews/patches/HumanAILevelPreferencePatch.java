package opusliews.patches;

/**
 * Level preference no longer intercepts or overrides the vanilla HumanAI tree.
 *
 * Preferences are soft weights used by SettlementCrossLevelJobSystem when there
 * is an actual cross-level work choice. Keeping this class without a
 * ModMethodPatch annotation also makes upgrades from older source snapshots
 * safe: overwriting the old patch file removes the runtime transformation.
 */
public final class HumanAILevelPreferencePatch {
	private HumanAILevelPreferencePatch() {
	}
}
