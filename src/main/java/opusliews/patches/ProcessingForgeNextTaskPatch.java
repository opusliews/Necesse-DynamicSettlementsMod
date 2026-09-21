package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.FueledProcessingInventoryObjectEntity;
import necesse.entity.objectEntity.FueledProcessingTechInventoryObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.forge.ForgeCookingSystem;

@ModMethodPatch(target = FueledProcessingTechInventoryObjectEntity.class, name = "getNextProcessTask", arguments = {})
public class ProcessingForgeNextTaskPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static FueledProcessingInventoryObjectEntity.NextProcessTask onEnter(
			@Advice.This FueledProcessingTechInventoryObjectEntity objectEntity
	) {
		if (!(objectEntity instanceof ProcessingForgeObjectEntity)) return null;
		return ForgeCookingSystem.getNextProcessTask((ProcessingForgeObjectEntity)objectEntity);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter FueledProcessingInventoryObjectEntity.NextProcessTask customTask,
			@Advice.Return(readOnly = false) FueledProcessingInventoryObjectEntity.NextProcessTask result
	) {
		if (customTask != null) result = customTask;
	}
}
