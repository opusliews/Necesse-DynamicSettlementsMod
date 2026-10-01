package opusliews;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

public class DynamicSettlementsSettings extends ModSettings {
	public static boolean enableInGameScaleShortcuts = true;

	@Override
	public void addSaveData(SaveData save) {
		save.addBoolean(
				"enableInGameScaleShortcuts",
				enableInGameScaleShortcuts,
				"Enables Ctrl+mouse-wheel zoom and Ctrl+numpad +/- interface scaling. Set to \"false\" to disable these shortcuts, to fix potential conflicts with other mods."
		);
	}

	@Override
	public void applyLoadData(LoadData save) {
		enableInGameScaleShortcuts = save.getBoolean("enableInGameScaleShortcuts", true);
	}
}
