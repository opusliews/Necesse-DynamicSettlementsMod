package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.container.Container;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.InventoryCraftingTime;

@ModMethodPatch(target = Container.class, name = "tick", arguments = {})
public class InventoryCraftingTickPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This Container container) {
		InventoryCraftingTime.tick(container);
	}
}
