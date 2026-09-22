package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.FueledProcessingInventoryObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.forge.ForgeAmbientSoundSystem;

@ModMethodPatch(
		target = FueledProcessingInventoryObjectEntity.class,
		name = "clientTick",
		arguments = {}
)
public class ProcessingForgeAmbientSoundPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This FueledProcessingInventoryObjectEntity objectEntity) {
		if (objectEntity instanceof ProcessingForgeObjectEntity) {
			ForgeAmbientSoundSystem.stopIfCold((ProcessingForgeObjectEntity)objectEntity);
		}
	}
}
