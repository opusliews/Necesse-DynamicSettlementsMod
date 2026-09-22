package opusliews.patches;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.forms.components.FormContainerRecipe;
import necesse.inventory.container.Container;
import necesse.inventory.container.ContainerRecipe;
import net.bytebuddy.asm.Advice;
import opusliews.forge.ForgeRequirementUI;

@ModMethodPatch(
		target = FormContainerRecipe.class,
		name = "getUsableError",
		arguments = {}
)
public class FormContainerRecipeForgeErrorPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.FieldValue("container") Container container,
			@Advice.FieldValue("recipe") ContainerRecipe recipe,
			@Advice.Return(readOnly = false) GameMessage result
	) {
		if (result == null && recipe != null) result = ForgeRequirementUI.getUsableError(container, recipe.recipe);
	}
}
