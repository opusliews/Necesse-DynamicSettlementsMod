package opusliews.patches;

import necesse.engine.localization.Localization;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.item.toolItem.shovelToolItem.ShovelToolItem;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = ShovelToolItem.class,
		name = "addToolTooltips",
		arguments = {ListGameTooltips.class}
)
public class ShovelHoleTooltipPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) ListGameTooltips tooltips) {
		tooltips.add(Localization.translate("itemtooltip", "shallowholedig"));
	}
}
