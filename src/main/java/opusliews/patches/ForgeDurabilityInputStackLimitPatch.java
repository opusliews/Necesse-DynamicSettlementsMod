package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.FueledProcessingInventoryObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.inventory.container.object.FueledProcessingOEInventoryContainer;
import necesse.inventory.container.slots.ContainerSlot;
import net.bytebuddy.asm.Advice;
import opusliews.durability.ItemDurabilitySystem;

@ModMethodPatch(
		target = ContainerSlot.class,
		name = "getItemStackLimit",
		arguments = {InventoryItem.class}
)
public class ForgeDurabilityInputStackLimitPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This ContainerSlot slot,
			@Advice.Argument(0) InventoryItem item,
			@Advice.Return(readOnly = false) int result
	) {
		if (!ItemDurabilitySystem.isBreakable(item)) return;
		if (!(slot.getContainer() instanceof FueledProcessingOEInventoryContainer)) return;

		FueledProcessingOEInventoryContainer container = (FueledProcessingOEInventoryContainer)slot.getContainer();
		FueledProcessingInventoryObjectEntity objectEntity = container.fueledProcessingObjectEntity;
		if (!(objectEntity instanceof ProcessingForgeObjectEntity)) return;
		if (slot.getInventory() != objectEntity.inventory) return;

		int inventorySlot = slot.getInventorySlot();
		int inputStart = objectEntity.fuelSlots;
		int inputEnd = inputStart + objectEntity.inputSlots;
		if (inventorySlot >= inputStart && inventorySlot < inputEnd) result = Math.min(result, 1);
	}
}
