package opusliews.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ForgeCookingRecipeRegistry {
	private static final int DEFAULT_PROCESS_TIME = 8000;
	private static final List<ForgeCookingRecipe> recipes = new ArrayList<>();

	private ForgeCookingRecipeRegistry() {
	}

	public static void registerRecipes() {
		registerMoldedToolHeads();
		registerMoldedBlades();
		registerThickIronPlateRecipes();
	}

	private static void registerMoldedToolHeads() {
		registerToolSet("copper", "copperbar", 8, "copperore", 32);
		registerToolSet("iron", "ironbar", 8, "ironore", 32);
		registerToolSet("gold", "goldbar", 8, "goldore", 32);
		registerToolSet("frost", "frostshard", 10, null, 0);
		registerToolSet("demonic", "demonicbar", 10, null, 0);
		registerToolSet("runic", "runestone", 10, null, 0);
		registerToolSet("ivy", "ivybar", 10, "ivyore", 40);
		registerToolSet("quartz", "quartz", 10, null, 0);
		registerToolSet("tungsten", "tungstenbar", 16, "tungstenore", 64);
		registerToolSet("glacial", "glacialbar", 16, "glacialore", 64);
		registerToolSet("dryad", "amber", 13, null, 0);
		registerToolSet("mycelium", "myceliumbar", 16, "myceliumore", 64);
		registerToolSet("ancientfossil", "ancientfossilbar", 16, "ancientfossilore", 64);

		registerMoldedPart("ironsickleblade", "sickleblademold", "ironbar", 10, "ironore", 40);
		registerMoldedPart("ironshearsblade", "shearsblademold", "ironbar", 10, "ironore", 40);
	}

	private static void registerMoldedBlades() {
		registerMoldedPart("copperblade", "swordblademold", "copperbar", 10, "copperore", 40);
		registerMoldedPart("ironblade", "swordblademold", "ironbar", 10, "ironore", 40);
		registerMoldedPart("goldblade", "swordblademold", "goldbar", 10, "goldore", 40);
		registerMoldedPart("tungstenblade", "swordblademold", "tungstenbar", 12, "tungstenore", 48);
		registerMoldedPart("demonicblade", "swordblademold", "demonicbar", 12, null, 0);
		registerMoldedPart("ivyblade", "swordblademold", "ivybar", 12, "ivyore", 48);
		registerMoldedPart("quartzblade", "swordblademold", "quartz", 20, null, 0);
		registerMoldedPart("glacialblade", "swordblademold", "glacialbar", 20, "glacialore", 80);
		registerMoldedPart("amethystblade", "swordblademold", "amethyst", 15, null, 0);
	}

	private static void registerToolSet(String material, String inputItem, int inputAmount, String oreItem, int oreAmount) {
		registerMoldedPart(material + "pickaxehead", "pickaxeheadmold", inputItem, inputAmount, oreItem, oreAmount);
		registerMoldedPart(material + "axehead", "axeheadmold", inputItem, inputAmount, oreItem, oreAmount);
		registerMoldedPart(material + "shovelhead", "shovelheadmold", inputItem, inputAmount, oreItem, oreAmount);
	}

	private static void registerMoldedPart(String outputItem, String moldItem, String inputItem, int inputAmount, String alternateItem, int alternateAmount) {
		register(new ForgeCookingRecipe(
				outputItem + "_from_" + inputItem,
				ForgeCookingInput.consume(inputItem, inputAmount),
				ForgeCookingInput.durabilityUse(moldItem),
				outputItem,
				1,
				DEFAULT_PROCESS_TIME
		));

		if (alternateItem != null && alternateAmount > 0) {
			register(new ForgeCookingRecipe(
					outputItem + "_from_" + alternateItem,
					ForgeCookingInput.consume(alternateItem, alternateAmount),
					ForgeCookingInput.durabilityUse(moldItem),
					outputItem,
					1,
					DEFAULT_PROCESS_TIME
			));
		}
	}

	private static void registerThickIronPlateRecipes() {
		register(new ForgeCookingRecipe(
				"thickironplate_from_bars",
				ForgeCookingInput.consume("ironbar", 4),
				ForgeCookingInput.durabilityUse("thickplatemold"),
				"thickironplate",
				1,
				DEFAULT_PROCESS_TIME
		));

		register(new ForgeCookingRecipe(
				"thickironplate_from_ore",
				ForgeCookingInput.consume("ironore", 16),
				ForgeCookingInput.durabilityUse("thickplatemold"),
				"thickironplate",
				1,
				DEFAULT_PROCESS_TIME
		));
	}

	public static void register(ForgeCookingRecipe recipe) {
		recipes.add(recipe);
	}

	public static List<ForgeCookingRecipe> getRecipes() {
		return Collections.unmodifiableList(recipes);
	}
}
