package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.FueledProcessingTechInventoryObjectEntity;
import necesse.inventory.InventoryItem;
import net.bytebuddy.asm.Advice;
import opusliews.forge.ForgeCookingSystem;

@ModMethodPatch(target = FueledProcessingTechInventoryObjectEntity.class, name = "isValidInputItem", arguments = {InventoryItem.class})
public class ProcessingForgeCustomInputPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This FueledProcessingTechInventoryObjectEntity objectEntity,
			@Advice.Argument(0) InventoryItem item, @Advice.Return(readOnly = false) boolean result) {
		if (!result && ForgeCookingSystem.isSupportedForge(objectEntity) && ForgeCookingSystem.isCustomInput(item)) result = true;
	}
}
