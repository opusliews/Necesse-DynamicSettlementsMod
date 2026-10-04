package opusliews.patches;

import java.util.ArrayList;
import java.util.function.Supplier;

import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.gfx.forms.presets.containerComponent.mob.DialogueForm;
import necesse.gfx.gameTooltips.GameTooltips;
import necesse.gfx.gameTooltips.StringTooltips;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelPreference;
import opusliews.multilevelsettlement.SettlementLevelPreferenceSystem;

@ModMethodPatch(target = DialogueForm.class, name = "getPersonalityData", arguments = {HumanMob.class})
public class SettlerLevelPreferenceDialogueTraitPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) HumanMob mob,
			@Advice.Return ArrayList result
	) {
		if (mob == null) {
			if (Logging.logEnabled) Logging.logMessage("[LevelPreferenceTrait] Could not add preference trait because mob was null");
			return;
		}
		if (result == null) {
			if (Logging.logEnabled) Logging.logMessage("[LevelPreferenceTrait] Could not add preference trait because personality data was null mob=" + mob.getUniqueID());
			return;
		}
		if (mob instanceof GuardHumanMob) return;

		SettlementLevelPreference preference = SettlementLevelPreferenceSystem.getPreference(mob);
		if (preference == SettlementLevelPreference.AUTO) return;

		String titleKey;
		String tooltipKey;
		if (preference == SettlementLevelPreference.SURFACE) {
			titleKey = "levelpreferencesurfacetrait";
			tooltipKey = "levelpreferencesurfacetip";
		}
		else if (preference == SettlementLevelPreference.CAVE) {
			titleKey = "levelpreferencecavetrait";
			tooltipKey = "levelpreferencecavetip";
		}
		else {
			if (Logging.logEnabled) Logging.logMessage("[LevelPreferenceTrait] Unsupported preference mob=" + mob.getUniqueID() + " preference=" + preference);
			return;
		}

		result.add(new DialogueForm.PersonalityData(
				new LocalMessage("ui", titleKey),
				false,
				new PreferenceTooltipSupplier(tooltipKey)
		));
	}

	public static class PreferenceTooltipSupplier implements Supplier<GameTooltips> {
		private final String localizationKey;

		public PreferenceTooltipSupplier(String localizationKey) {
			this.localizationKey = localizationKey;
		}

		@Override
		public GameTooltips get() {
			StringTooltips tooltips = new StringTooltips();
			tooltips.add(Localization.translate("ui", localizationKey));
			return tooltips;
		}
	}
}
