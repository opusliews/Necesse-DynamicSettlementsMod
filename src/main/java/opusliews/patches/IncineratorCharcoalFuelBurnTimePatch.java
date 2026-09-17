package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.FueledIncineratorObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.CharcoalFuelSystem;

@ModMethodPatch(
		target = FueledIncineratorObjectEntity.class,
		name = "getNextFuelBurnTime",
		arguments = {boolean.class}
)
public class IncineratorCharcoalFuelBurnTimePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter() {
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This FueledIncineratorObjectEntity objectEntity,
			@Advice.Argument(0) boolean useFuel,
			@Advice.Return(readOnly = false) int result
	) {
		result = CharcoalFuelSystem.consumeFuel(
				objectEntity.getInventory(),
				0,
				objectEntity.fuelSlots - 1,
				useFuel,
				FueledIncineratorObjectEntity.FUEL_TIME_ADDED
		);
	}
}
