package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.AnyLogFueledProcessingTechInventoryObjectEntity;
import necesse.inventory.InventoryItem;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.CharcoalFuelSystem;
import opusliews.forge.ForgeHeatSystem;

@ModMethodPatch(
		target = AnyLogFueledProcessingTechInventoryObjectEntity.class,
		name = "getNextFuelBurnTime",
		arguments = {boolean.class}
)
public class ProcessingCharcoalFuelBurnTimePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter() {
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This AnyLogFueledProcessingTechInventoryObjectEntity objectEntity,
			@Advice.Argument(0) boolean useFuel,
			@Advice.Return(readOnly = false) int result
	) {
		if (ForgeHeatSystem.shouldBlockAutomaticIdleFuelUse(objectEntity, useFuel)) {
			result = 0;
			return;
		}

		InventoryItem charcoal = findCharcoal(objectEntity);
		int burnTime = charcoal == null ? 0 : objectEntity.getFuelTime(charcoal);
		result = CharcoalFuelSystem.consumeFuel(
				objectEntity.getInventory(),
				0,
				objectEntity.fuelSlots - 1,
				useFuel,
				burnTime
		);
	}

	public static InventoryItem findCharcoal(AnyLogFueledProcessingTechInventoryObjectEntity objectEntity) {
		for (int i = objectEntity.fuelSlots - 1; i >= 0; i--) {
			InventoryItem item = objectEntity.getInventory().getItem(i);
			if (CharcoalFuelSystem.isCharcoal(item)) {
				return item;
			}
		}
		return null;
	}
}
