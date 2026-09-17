package opusliews.charcoal;

import opusliews.forms.CharcoalProductionSettingsForm;

public final class CharcoalProductionClientSettings {
	public static int produceUntilUnitsStocked;
	public static boolean repeatForever;
	public static CharcoalProductionSettingsForm openForm;

	private CharcoalProductionClientSettings() {
	}

	public static void apply(int target, boolean repeat) {
		produceUntilUnitsStocked = Math.max(0, target);
		repeatForever = repeat;
		if (openForm != null && !openForm.isDisposed()) {
			openForm.applySettings(produceUntilUnitsStocked, repeatForever);
		}
	}
}
