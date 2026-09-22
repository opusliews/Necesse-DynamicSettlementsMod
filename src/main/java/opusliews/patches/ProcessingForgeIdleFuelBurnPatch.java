package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.FueledProcessingInventoryObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = FueledProcessingInventoryObjectEntity.class,
		name = "serverTick",
		arguments = {}
)
public class ProcessingForgeIdleFuelBurnPatch {
	@Advice.OnMethodEnter
	public static boolean onEnter(
			@Advice.This FueledProcessingInventoryObjectEntity objectEntity,
			@Advice.FieldValue(value = "remainingFuelTime") int remainingFuelTime,
			@Advice.FieldValue(value = "keepFuelRunning", readOnly = false) boolean keepFuelRunning,
			@Advice.FieldValue(value = "fuelPaused", readOnly = false) boolean fuelPaused
	) {
		if (!(objectEntity instanceof ProcessingForgeObjectEntity) || remainingFuelTime <= 0) return keepFuelRunning;

		boolean oldKeepFuelRunning = keepFuelRunning;
		keepFuelRunning = true;
		if (fuelPaused) {
			fuelPaused = false;
			objectEntity.markProgressDirty();
		}
		return oldKeepFuelRunning;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This FueledProcessingInventoryObjectEntity objectEntity,
			@Advice.Enter boolean oldKeepFuelRunning,
			@Advice.FieldValue(value = "keepFuelRunning", readOnly = false) boolean keepFuelRunning
	) {
		if (objectEntity instanceof ProcessingForgeObjectEntity) keepFuelRunning = oldKeepFuelRunning;
	}
}
