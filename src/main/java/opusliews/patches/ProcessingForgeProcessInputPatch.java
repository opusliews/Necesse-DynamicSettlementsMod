package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.FueledProcessingTechInventoryObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.forge.ForgeCookingSystem;

@ModMethodPatch(target = FueledProcessingTechInventoryObjectEntity.class, name = "processInput", arguments = {})
public class ProcessingForgeProcessInputPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This FueledProcessingTechInventoryObjectEntity objectEntity) {
		return ForgeCookingSystem.findMatch(objectEntity) != null;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.This FueledProcessingTechInventoryObjectEntity objectEntity,
			@Advice.Enter boolean customRecipe, @Advice.Return(readOnly = false) boolean result) {
		if (customRecipe) result = ForgeCookingSystem.processInput(objectEntity);
	}
}
