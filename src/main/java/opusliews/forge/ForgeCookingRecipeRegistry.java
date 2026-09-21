package opusliews.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ForgeCookingRecipeRegistry {
	private static final List<ForgeCookingRecipe> recipes = new ArrayList<>();

	private ForgeCookingRecipeRegistry() {
	}

	public static void registerRecipes() {
		register(new ForgeCookingRecipe(
				"thickironplate_from_bars",
				ForgeCookingInput.consume("ironbar", 4),
				ForgeCookingInput.durabilityUse("thickplatemold"),
				"thickironplate",
				1,
				8000
		));

		register(new ForgeCookingRecipe(
				"thickironplate_from_ore",
				ForgeCookingInput.consume("ironore", 16),
				ForgeCookingInput.durabilityUse("thickplatemold"),
				"thickironplate",
				1,
				8000
		));
	}

	public static void register(ForgeCookingRecipe recipe) {
		recipes.add(recipe);
	}

	public static List<ForgeCookingRecipe> getRecipes() {
		return Collections.unmodifiableList(recipes);
	}
}
