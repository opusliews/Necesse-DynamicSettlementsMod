package opusliews.crafting;

import necesse.engine.registries.GlobalIngredientRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.RecipeTechRegistry;
import necesse.inventory.recipe.GlobalIngredient;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VanillaRecipeRework {
	private static final Map<String, IngredientSpec[]> replacements = loadReplacementSpecs();
	private static final Field ingredientsField;
	private static final Field calculatedHashField;

	static {
		try {
			ingredientsField = Recipe.class.getDeclaredField("ingredients");
			ingredientsField.setAccessible(true);
			calculatedHashField = Recipe.class.getDeclaredField("calculatedHash");
			calculatedHashField.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Could not initialize Dynamic Settlements recipe rework reflection", e);
		}
	}

	public static void apply(ArrayList recipes) {
		recipes.removeIf(value -> {
			if (!(value instanceof Recipe)) return false;
			Recipe recipe = (Recipe)value;
			String resultID = recipe.resultStringID;
			if ("woodaxe".equals(resultID)
					|| "ladderdown".equals(resultID)
					|| "deepladderdown".equals(resultID)
					|| "brickwall".equals(resultID)
					|| "torch".equals(resultID)
					|| "woodshovel".equals(resultID)
					|| "woodsword".equals(resultID)
					|| "woodpickaxe".equals(resultID)
					|| "forge".equals(resultID)
					|| "campfire".equals(resultID)
					|| "roastingstation".equals(resultID)
					|| "ironanvil".equals(resultID)
					|| "workstationduo".equals(resultID)) return true;

			if (recipe.tech == RecipeTechRegistry.NONE) {
				switch (resultID) {
					case "torch":
					case "workstationduo":
					case "woodboat":
					case "stonearrow":
						return true;
				}
			}

			return isLegacyBarRecipe(recipe);
		});

		Set<String> registeredGlobalIngredients = getRegisteredGlobalIngredientIDs();

		for (Object value : recipes) {
			Recipe recipe = (Recipe)value;
			IngredientSpec[] specs = replacements.get(key(recipe.resultStringID, recipe.tech.getStringID()));
			if (specs == null) {
				continue;
			}

			Ingredient[] ingredients = new Ingredient[specs.length];
			for (int i = 0; i < specs.length; i++) {
				IngredientSpec spec = specs[i];
				validateIngredient(spec.stringID, registeredGlobalIngredients, recipe);
				ingredients[i] = new Ingredient(spec.stringID, spec.amount);
			}

			try {
				ingredientsField.set(recipe, ingredients);
				calculatedHashField.setInt(recipe, 0);
			} catch (IllegalAccessException e) {
				throw new RuntimeException("Could not replace vanilla recipe ingredients for " + recipe.resultStringID, e);
			}
		}
	}

	private static boolean isLegacyBarRecipe(Recipe recipe) {
		if (recipe == null || recipe.resultStringID == null) return false;
		if ("demonicbar".equals(recipe.resultStringID)) return true;
		if (recipe.tech != RecipeTechRegistry.FORGE) return false;

		switch (recipe.resultStringID) {
			case "copperbar":
			case "ironbar":
			case "goldbar":
			case "tungstenbar":
			case "glacialbar":
			case "myceliumbar":
			case "ancientfossilbar":
			case "nightsteelbar":
			case "spideritebar":
			case "ivybar":
				return true;
			default:
				return false;
		}
	}

	private static Map<String, IngredientSpec[]> loadReplacementSpecs() {
		Map<String, IngredientSpec[]> out = new HashMap<>();
		InputStream stream = VanillaRecipeRework.class.getResourceAsStream("/resources/crafting_recipe_rework.tsv");
		if (stream == null) {
			throw new IllegalStateException("Missing resources/crafting_recipe_rework.tsv");
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			int lineNumber = 0;

			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isEmpty() || line.charAt(0) == '#') {
					continue;
				}

				String[] columns = line.split("\\t", -1);
				if (columns.length != 3) {
					throw new IllegalStateException("Invalid crafting recipe rework line " + lineNumber + ": " + line);
				}

				String[] ingredientSpecs = columns[2].isEmpty() ? new String[0] : columns[2].split(",");
				IngredientSpec[] specs = new IngredientSpec[ingredientSpecs.length];

				for (int i = 0; i < ingredientSpecs.length; i++) {
					String ingredientSpec = ingredientSpecs[i].trim();
					int split = ingredientSpec.lastIndexOf(':');
					if (split <= 0 || split == ingredientSpec.length() - 1) {
						throw new IllegalStateException("Invalid ingredient spec on line " + lineNumber + ": " + ingredientSpec);
					}

					String ingredientID = ingredientSpec.substring(0, split).trim();
					int amount = Integer.parseInt(ingredientSpec.substring(split + 1).trim());
					specs[i] = new IngredientSpec(ingredientID, amount);
				}

				String replacementKey = key(columns[0].trim(), columns[1].trim());
				if (out.put(replacementKey, specs) != null) {
					throw new IllegalStateException("Duplicate crafting recipe rework entry on line " + lineNumber + ": " + replacementKey);
				}
			}
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Could not load Dynamic Settlements crafting recipe rework", e);
		}

		return out;
	}

	private static Set<String> getRegisteredGlobalIngredientIDs() {
		Set<String> out = new HashSet<>();
		for (GlobalIngredient ingredient : GlobalIngredientRegistry.getGlobalIngredients()) {
			out.add(ingredient.getStringID());
		}
		return out;
	}

	private static void validateIngredient(String ingredientID, Set<String> registeredGlobalIngredients, Recipe recipe) {
		if (ItemRegistry.getItemID(ingredientID) != -1 || registeredGlobalIngredients.contains(ingredientID)) {
			return;
		}

		throw new IllegalStateException(
				"Unknown crafting rework ingredient \"" + ingredientID
						+ "\" for recipe \"" + recipe.resultStringID
						+ "\" at tech \"" + recipe.tech.getStringID() + "\". "
						+ "It must be a registered item or global ingredient before default recipes are loaded."
		);
	}

	private static String key(String resultID, String techID) {
		return resultID + "\t" + techID;
	}

	private static class IngredientSpec {
		private final String stringID;
		private final int amount;

		private IngredientSpec(String stringID, int amount) {
			this.stringID = stringID;
			this.amount = amount;
		}
	}
}
