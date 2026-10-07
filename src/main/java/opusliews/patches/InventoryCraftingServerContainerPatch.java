package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.InventoryCraftingTime;

@ModMethodPatch(target = ServerClient.class, name = "updateInventoryContainer", arguments = {})
public class InventoryCraftingServerContainerPatch {
	@Advice.OnMethodEnter
	public static Container onEnter(@Advice.FieldValue("inventoryContainer") Container inventoryContainer) {
		return inventoryContainer;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter Container oldContainer,
			@Advice.FieldValue("inventoryContainer") Container newContainer
	) {
		InventoryCraftingTime.onInventoryContainerReplaced(oldContainer, newContainer, "server");
	}
}
