package opus.config;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

public class SettlementBuildersSettings extends ModSettings {
	public boolean hardcoreFeatures = true;

	@Override
	public void addSaveData(SaveData save) {
		save.addBoolean(
				"hardcoreFeatures",
				hardcoreFeatures,
				"If true, enables Settlement Builders hardcore features"
		);
	}

	@Override
	public void applyLoadData(LoadData save) {
		// Keep the old key as a fallback so existing configs continue to work.
		boolean legacyValue = save.getBoolean("hardcoreDamage", true, false);
		hardcoreFeatures = save.getBoolean("hardcoreFeatures", legacyValue, false);
	}
}
