package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.stock.SettlementStockSystem;

@ModMethodPatch(
		target = ItemCategoriesFilter.class,
		name = "getRemoveAmount",
		arguments = {Level.class, InventoryItem.class, InventoryRange.class}
)
public class ItemCategoriesFilterStockRemovePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This ItemCategoriesFilter filter,
			@Advice.Argument(1) InventoryItem item,
			@Advice.Argument(2) InventoryRange range,
			@Advice.Return(readOnly = false) int result
	) {
		result = SettlementStockSystem.overrideRemoveAmount(filter, item, range, result);
	}
}
