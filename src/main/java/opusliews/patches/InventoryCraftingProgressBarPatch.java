package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.MainGameFormManager;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.InventoryCraftingUI;

@ModMethodPatch(target = MainGameFormManager.class, name = "setup", arguments = {})
public class InventoryCraftingProgressBarPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This MainGameFormManager manager,
			@Advice.FieldValue("client") Client client
	) {
		InventoryCraftingUI.setup(manager, client);
	}
}
