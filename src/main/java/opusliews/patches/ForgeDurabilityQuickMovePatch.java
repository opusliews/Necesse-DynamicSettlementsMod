package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import necesse.inventory.container.Container;
import necesse.inventory.container.object.FueledProcessingOEInventoryContainer;
import necesse.inventory.container.slots.ContainerSlot;
import net.bytebuddy.asm.Advice;
import opusliews.durability.ItemDurabilitySystem;

@ModMethodPatch(
		target = Container.class,
		name = "transferFromAmount",
		arguments = {int.class, ContainerSlot.class, int.class}
)
public class ForgeDurabilityQuickMovePatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This Container container,
			@Advice.Argument(0) int slotIndex,
			@Advice.Argument(1) ContainerSlot slot,
			@Advice.Argument(value = 2, readOnly = false) int amount
	) {
		if (amount <= 1 || slot == null || slot.isClear()) return;
		if (!(container instanceof FueledProcessingOEInventoryContainer)) return;

		FueledProcessingOEInventoryContainer forgeContainer = (FueledProcessingOEInventoryContainer)container;
		if (!(forgeContainer.fueledProcessingObjectEntity instanceof ProcessingForgeObjectEntity)) return;
		if (!ItemDurabilitySystem.isBreakable(slot.getItem())) return;

		boolean playerSlot = slotIndex >= container.CLIENT_HOTBAR_START && slotIndex <= container.CLIENT_HOTBAR_END;
		playerSlot |= slotIndex >= container.CLIENT_INVENTORY_START && slotIndex <= container.CLIENT_INVENTORY_END;
		if (playerSlot) amount = 1;
	}
}
