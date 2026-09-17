package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.AnyLogFueledInventoryObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.CharcoalFuelSystem;

@ModMethodPatch(
		target = AnyLogFueledInventoryObjectEntity.class,
		name = "getNextFuelBurnTime",
		arguments = {boolean.class}
)
public class AnyLogFuelBurnTimePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter() {
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This AnyLogFueledInventoryObjectEntity objectEntity,
			@Advice.Argument(0) boolean useFuel,
			@Advice.Return(readOnly = false) int result
	) {
		result = CharcoalFuelSystem.consumeFuel(
				objectEntity.getInventory(),
				0,
				objectEntity.getInventory().getSize() - 1,
				useFuel,
				120000
		);
	}
}
