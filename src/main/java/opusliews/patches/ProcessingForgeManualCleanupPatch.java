package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.FueledProcessingInventoryObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.forge.ForgeManualCleanupSystem;

@ModMethodPatch(
		target = FueledProcessingInventoryObjectEntity.class,
		name = "serverTick",
		arguments = {}
)
public class ProcessingForgeManualCleanupPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This FueledProcessingInventoryObjectEntity objectEntity) {
		if (objectEntity instanceof ProcessingForgeObjectEntity) {
			ForgeManualCleanupSystem.serverTick((ProcessingForgeObjectEntity)objectEntity);
		}
	}
}
