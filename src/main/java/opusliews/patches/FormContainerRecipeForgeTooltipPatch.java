package opusliews.patches;

import java.awt.Rectangle;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.FormContainerRecipe;
import necesse.inventory.container.Container;
import necesse.inventory.container.ContainerRecipe;
import net.bytebuddy.asm.Advice;
import opusliews.forge.ForgeRequirementUI;

@ModMethodPatch(
		target = FormContainerRecipe.class,
		name = "draw",
		arguments = {TickManager.class, PlayerMob.class, Rectangle.class}
)
public class FormContainerRecipeForgeTooltipPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This FormContainerRecipe component,
			@Advice.FieldValue("container") Container container,
			@Advice.FieldValue("recipe") ContainerRecipe recipe
	) {
		if (recipe != null) ForgeRequirementUI.addRequirementTooltip(container, recipe.recipe, component.isHovering());
	}
}
