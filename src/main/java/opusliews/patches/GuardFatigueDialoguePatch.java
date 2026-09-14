package opusliews.patches;

import java.util.ArrayList;
import java.util.function.Supplier;

import necesse.engine.localization.Localization;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.gfx.forms.presets.containerComponent.mob.DialogueForm;
import necesse.gfx.gameTooltips.GameTooltips;
import necesse.gfx.gameTooltips.StringTooltips;
import net.bytebuddy.asm.Advice;
import opusliews.guard.GuardFatigueSystem;

@ModMethodPatch(target = DialogueForm.class, name = "getPersonalityData", arguments = {HumanMob.class})
public class GuardFatigueDialoguePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) HumanMob mob,
			@Advice.Return ArrayList result
	) {
		if (!(mob instanceof GuardHumanMob) || result == null) {
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		int fatigue = GuardFatigueSystem.getFatigue(guard);
		if (fatigue <= 0) {
			return;
		}

		result.add(new DialogueForm.PersonalityData(
				new StaticMessage("Fatigue Lvl " + fatigue),
				false,
				new FatigueTooltipSupplier(fatigue)
		));
	}

	public static class FatigueTooltipSupplier implements Supplier<GameTooltips> {
		private final int fatigue;

		public FatigueTooltipSupplier(int fatigue) {
			this.fatigue = fatigue;
		}

		@Override
		public GameTooltips get() {
			int penalty = fatigue * 10;
			StringTooltips tooltips = new StringTooltips();
			if (fatigue < 10) {
				tooltips.add(Localization.translate(
						"ui",
						"fatiguedmgout",
						"penalty",
						penalty));

				tooltips.add(Localization.translate(
						"ui",
						"fatiguemovespeed",
						"penalty",
						penalty));

				tooltips.add(Localization.translate(
						"ui",
						"fatiguedmgin",
						"penalty",
						penalty));

				tooltips.add(Localization.translate(
						"ui",
						"fatiguehelp"));

			}
			else {
				tooltips.add(Localization.translate(
						"ui",
						"fatigueincapacitated"));

				tooltips.add(Localization.translate(
						"ui",
						"fatiguehelp"));
			}

			return tooltips;
		}
	}
}
