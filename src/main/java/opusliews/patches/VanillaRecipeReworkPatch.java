package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.inventory.recipe.Recipes;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.VanillaRecipeRework;

import java.util.ArrayList;

@ModMethodPatch(
		target = Recipes.class,
		name = "getDefaultRecipes",
		arguments = {}
)
public class VanillaRecipeReworkPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return ArrayList result) {
		VanillaRecipeRework.apply(result);
	}
}
