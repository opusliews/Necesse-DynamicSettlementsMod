package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.inventory.container.Container;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.InventoryCraftingTime;

@ModMethodPatch(target = Client.class, name = "initInventoryContainer", arguments = {})
public class InventoryCraftingClientContainerPatch {
	@Advice.OnMethodEnter
	public static Container onEnter(@Advice.FieldValue("inventoryContainer") Container inventoryContainer) {
		return inventoryContainer;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter Container oldContainer,
			@Advice.FieldValue("inventoryContainer") Container newContainer
	) {
		InventoryCraftingTime.onInventoryContainerReplaced(oldContainer, newContainer, "client");
	}
}
