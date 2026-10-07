package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.MainGameFormManager;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.InventoryCraftingUI;

@ModMethodPatch(target = MainGameFormManager.class, name = "updateInventoryForm", arguments = {})
public class InventoryCraftingInventoryFormUpdatePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This MainGameFormManager manager,
			@Advice.FieldValue("client") Client client
	) {
		InventoryCraftingUI.onInventoryFormUpdated(manager, client);
	}
}
