//package opusliews.patches;
//
//import necesse.engine.localization.Localization;
//import necesse.engine.modLoader.annotations.ModMethodPatch;
//import necesse.gfx.gameTooltips.ListGameTooltips;
//import net.bytebuddy.asm.Advice;
//import opusliews.item.TreasureShovelItem;
//
//@ModMethodPatch(
//		target = TreasureShovelItem.class,
//		name = "addToolTooltips",
//		arguments = {ListGameTooltips.class}
//)
//public class TreasureShovelHoleTooltipPatch {
//	@Advice.OnMethodExit
//	public static void onExit(@Advice.Argument(0) ListGameTooltips tooltips) {
//		tooltips.add(Localization.translate("itemtooltip", "shallowholedig"));
//		tooltips.add(Localization.translate("itemtooltip", "treasureshovel"));
//	}
//}
