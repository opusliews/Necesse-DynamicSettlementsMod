package opusliews.patches;

import necesse.engine.Settings;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.GameTooltips;
import net.bytebuddy.asm.Advice;
import opusliews.progression.EarlyHealthProgressionSystem;

@ModMethodPatch(
		target = MainGame.class,
		name = "drawStatusIconsCentered",
		arguments = {int.class, int.class, GameTexture.class, GameTexture.class, float.class, float.class, float.class, int.class, int.class, int.class, GameTooltips.class}
)
public class HungerPipCountPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.Argument(2) GameTexture outline,
			@Advice.Argument(value = 6, readOnly = false) float valuePerIcon,
			@Advice.Argument(value = 7, readOnly = false) int iconsPerRow
	) {
		if (outline != Settings.UI.food_outline) return;
		int pips = EarlyHealthProgressionSystem.getCurrentClientHungerPips();
		iconsPerRow = pips;
		valuePerIcon = 1.0F / pips;
	}
}
