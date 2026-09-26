package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.MainGameFormManager;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.InventoryCraftingUI;

@ModMethodPatch(target = MainGameFormManager.class, name = "updateActive", arguments = {boolean.class})
public class InventoryCraftingVisibilityPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This MainGameFormManager manager) {
		InventoryCraftingUI.updateVisibility(manager);
	}
}