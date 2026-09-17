package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.AnyLogFueledProcessingTechInventoryObjectEntity;
import necesse.inventory.InventoryItem;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.CharcoalFuelSystem;

@ModMethodPatch(
		target = AnyLogFueledProcessingTechInventoryObjectEntity.class,
		name = "isValidFuelItem",
		arguments = {InventoryItem.class}
)
public class ProcessingCharcoalFuelValidationPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) InventoryItem item,
			@Advice.Return(readOnly = false) boolean result
	) {
		result = CharcoalFuelSystem.isCharcoal(item);
	}
}
