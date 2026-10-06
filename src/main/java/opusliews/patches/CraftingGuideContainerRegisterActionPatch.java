package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.inventory.container.item.CraftingGuideContainer;
import net.bytebuddy.asm.Advice;
import opusliews.craftingguide.CraftingGuideExtension;

@ModMethodPatch(target = CraftingGuideContainer.class, name = "<init>", arguments = {NetworkClient.class, int.class, Packet.class})
public class CraftingGuideContainerRegisterActionPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This CraftingGuideContainer container) {
		CraftingGuideExtension.registerContainer(container);
	}
}
