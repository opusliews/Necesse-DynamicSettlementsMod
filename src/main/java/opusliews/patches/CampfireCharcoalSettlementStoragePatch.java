package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.CampfireObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.CharcoalFuelSystem;

@ModMethodPatch(
		target = CampfireObjectEntity.class,
		name = "isSettlementStorageItemDisabled",
		arguments = {Item.class}
)
public class CampfireCharcoalSettlementStoragePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) Item item,
			@Advice.Return(readOnly = false) boolean result
	) {
		result = item == null || !CharcoalFuelSystem.isCookingFuel(new InventoryItem(item));
	}
}
