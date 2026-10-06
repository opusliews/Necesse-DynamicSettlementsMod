package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.presets.containerComponent.item.CraftingGuideContainerForm;
import necesse.inventory.container.item.CraftingGuideContainer;
import net.bytebuddy.asm.Advice;
import opusliews.craftingguide.CraftingGuideExtension;

@ModMethodPatch(target = CraftingGuideContainerForm.class, name = "<init>", arguments = {Client.class, CraftingGuideContainer.class})
public class CraftingGuideContainerFormSetupPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This CraftingGuideContainerForm form,
			@Advice.Argument(0) Client client,
			@Advice.Argument(1) CraftingGuideContainer container) {
		CraftingGuideExtension.setupForm(form, client, container);
	}
}
