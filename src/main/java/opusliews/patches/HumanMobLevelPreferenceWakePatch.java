package opusliews.patches;

/**
 * Level preference is no longer implemented as a dedicated AI node that needs
 * waking from HumanMob.serverTick(). The preference is read only when an actual
 * cross-level job choice is made.
 *
 * This intentionally has no ModMethodPatch annotation so older installs that
 * overwrite this source file stop transforming HumanMob.serverTick().
 */
public final class HumanMobLevelPreferenceWakePatch {
	private HumanMobLevelPreferenceWakePatch() {
	}
}
