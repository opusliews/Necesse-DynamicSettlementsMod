package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.AnyLogFueledInventoryObjectEntity;
import necesse.inventory.InventoryItem;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.CharcoalFuelSystem;

@ModMethodPatch(
		target = AnyLogFueledInventoryObjectEntity.class,
		name = "isValidFuelItem",
		arguments = {int.class, InventoryItem.class}
)
public class AnyLogFuelValidationPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(1) InventoryItem item,
			@Advice.Return(readOnly = false) boolean result
	) {
		result = CharcoalFuelSystem.isCharcoal(item);
	}
}
