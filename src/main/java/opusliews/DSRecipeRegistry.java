package opusliews;

import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.registries.RecipeTechRegistry;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;
import opusliews.object.BuilderJobRequestBulletinObject;
import opusliews.object.WarningBellObject;

import static opusliews.DSItemRegistry.*;

@ModEntry
public class DSRecipeRegistry {
	public static void registerRecipes() {
		Recipes.registerModRecipe(new Recipe(
				"blueprintItem",
				1,
				RecipeTechRegistry.WORKSTATION,
				new Ingredient[]{
						new Ingredient("stackofpaper", 1),
						new Ingredient("quillandparchment", 1)
				}
		));

		Recipes.registerModRecipe(new Recipe(
				"blueprintworkstation",
				1,
				RecipeTechRegistry.WORKSTATION,
				new Ingredient[]{
						new Ingredient("anylog", 15),
						new Ingredient("tungstenbar", 3),
						new Ingredient("stackofpaper", 1)
				}
		));

		Recipes.registerModRecipe(new Recipe(
				"builderhat",
				1,
				RecipeTechRegistry.WORKSTATION,
				new Ingredient[]{
						new Ingredient("wool", 12),
						new Ingredient("ironbar", 1)
				}
		));

		Recipes.registerModRecipe(new Recipe(
				"buildershirt",
				1,
				RecipeTechRegistry.WORKSTATION,
				new Ingredient[]{
						new Ingredient("wool", 16)
				}
		));

		Recipes.registerModRecipe(new Recipe(
				"builderboots",
				1,
				RecipeTechRegistry.WORKSTATION,
				new Ingredient[]{
						new Ingredient("wool", 8),
						new Ingredient("leather", 1)
				}
		));

		Recipes.registerModRecipe(new Recipe(
				"projecteraser",
				1,
				RecipeTechRegistry.WORKSTATION,
				new Ingredient[]{
						new Ingredient("quillandparchment", 1),
						new Ingredient("ironbar", 1)
				}
		));

		Recipes.registerModRecipe(new Recipe(
				"inspectionglass",
				1,
				RecipeTechRegistry.WORKSTATION,
				new Ingredient[]{
						new Ingredient("glass", 2),
						new Ingredient("ironbar", 1)
				}
		));

		Recipes.registerModRecipe(new Recipe(
				"malignancegoggles",
				1,
				RecipeTechRegistry.DEMONIC_WORKSTATION,
				new Ingredient[]{
						new Ingredient("inspectionglass", 2),
						new Ingredient("demonicbar", 1)
				}
		));

		Recipes.registerModRecipe(new Recipe(
				BuilderJobRequestBulletinObject.stringID,
				1,
				RecipeTechRegistry.WORKSTATION,
				new Ingredient[]{
						new Ingredient("stackofpaper", 1),
						new Ingredient("quillandparchment", 1)
				}
		));

		Recipes.registerModRecipe(new Recipe(
				WarningBellObject.stringID,
				1,
				RecipeTechRegistry.WORKSTATION,
				new Ingredient[]{
						new Ingredient("goldbar", 3),
						new Ingredient("ironbar", 1)
				}
		));

		Recipes.registerModRecipe(new Recipe(
				stringStringID,
				1,
				RecipeTechRegistry.WORKSTATION,
				new Ingredient[]{new Ingredient("grass", 4)}
		));

		Recipes.registerModRecipe(new Recipe(
				groundFiremoneStringID,
				1,
				RecipeTechRegistry.GRAIN_MILL,
				new Ingredient[]{new Ingredient("firemone", 1)}
		));

		Recipes.registerModRecipe(new Recipe(
				waxStringID,
				1,
				RecipeTechRegistry.COOKING_POT,
				new Ingredient[]{new Ingredient(honeycombFragmentStringID, 1)}
		));
	}
}
