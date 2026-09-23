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
		name = "getAddAmount",
		arguments = {Level.class, InventoryItem.class, InventoryRange.class, boolean.class}
)
public class ItemCategoriesFilterStockAddPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This ItemCategoriesFilter filter,
			@Advice.Argument(1) InventoryItem item,
			@Advice.Argument(2) InventoryRange range,
			@Advice.Return(readOnly = false) int result
	) {
		result = SettlementStockSystem.overrideAddAmount(filter, item, range, result);
	}
}
