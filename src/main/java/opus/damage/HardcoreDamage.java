package opus.damage;

import necesse.level.maps.Level;

/**
 * @deprecated Use {@link HardcoreFeatures}. Kept as a compatibility shim for
 * older integrations that may still reference the former helper name. The legacy
 * hardcoreDamage config key is handled by SettlementBuildersSettings.
 */
@Deprecated
public final class HardcoreDamage {
	private HardcoreDamage() {
	}

	public static boolean isServerEnabled() {
		return HardcoreFeatures.isServerEnabled();
	}

	public static boolean isEnabled(Level level) {
		return HardcoreFeatures.isEnabled(level);
	}

	public static void setClientEnabled(boolean enabled) {
		HardcoreFeatures.setClientEnabled(enabled);
	}
}
