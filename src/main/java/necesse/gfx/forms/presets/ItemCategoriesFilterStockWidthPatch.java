package necesse.gfx.forms.presets;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import net.bytebuddy.asm.Advice;
import opusliews.stock.SettlementStockUI;

@ModMethodPatch(
		target = ItemCategoriesFilterForm.ItemForm.class,
		name = "fixDimensions",
		arguments = {}
)
public class ItemCategoriesFilterStockWidthPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ItemCategoriesFilterForm.ItemForm form) {
		SettlementStockUI.restoreAdvancedRowWidth(form);
	}
}
