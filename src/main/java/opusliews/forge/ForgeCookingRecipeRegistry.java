package opusliews.forge;

import opusliews.DSItemRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ForgeCookingRecipeRegistry {
	private static final int DEFAULT_PROCESS_TIME = 8000;
	private static final List<ForgeCookingRecipe> recipes = new ArrayList<>();
	private static final Set<String> forgeWorkedIngredients = new HashSet<>();

	private ForgeCookingRecipeRegistry() {
	}

	public static void registerRecipes() {
		registerMoldedBars();
		registerMoldedToolHeads();
		registerMoldedBlades();
		registerSawBlade();
		registerThickIronPlateRecipes();
		registerOtherRecipes();
	}

	private static void registerOtherRecipes() {
		register(ForgeCookingRecipe.fractionOfDay(
				"brick_forge_cooked",
				ForgeCookingInput.consume(DSItemRegistry.unfiredBrickStringID, 1),
				null,
				DSItemRegistry.brickStringID,
				1,
				0.5f
		));

	}
	private static void registerMoldedBars() {
		registerMoldedBar("copperbar", "copperore", 4);
		registerMoldedBar("ironbar", "ironore", 4);
		registerMoldedBar("goldbar", "goldore", 4);
		registerMoldedBar("tungstenbar", "tungstenore", 4);
		registerMoldedBar("glacialbar", "glacialore", 4);
		registerMoldedBar("myceliumbar", "myceliumore", 4);
		registerMoldedBar("ancientfossilbar", "ancientfossilore", 4);
		registerMoldedBar("nightsteelbar", "nightsteelore", 4);
		registerMoldedBar("spideritebar", "spideriteore", 4);
		registerMoldedBar("ivybar", "ivyore", 4);

		register(new ForgeCookingRecipe(
				"ironbar_from_brokenirontool",
				ForgeCookingInput.durabilityUse("brokenirontool"),
				ForgeCookingInput.durabilityUse("ingotmold"),
				"ironbar",
				1,
				DEFAULT_PROCESS_TIME
		));
		register(new ForgeCookingRecipe(
				"copperbar_from_brokencoppertool",
				ForgeCookingInput.durabilityUse("brokencoppertool"),
				ForgeCookingInput.durabilityUse("ingotmold"),
				"copperbar",
				1,
				DEFAULT_PROCESS_TIME
		));

		registerMoldedBarFromMaterial("demonicbar", "copperbar", 3);
		registerMoldedBarFromMaterial("demonicbar", "ironbar", 2);
		registerMoldedBarFromMaterial("demonicbar", "goldbar", 1);


	}

	private static void registerMoldedBar(String outputItem, String oreItem, int oreAmount) {
		registerMoldedBarFromMaterial(outputItem, oreItem, oreAmount);
	}

	private static void registerMoldedBarFromMaterial(String outputItem, String inputItem, int inputAmount) {
		register(new ForgeCookingRecipe(
				outputItem + "_from_" + inputItem,
				ForgeCookingInput.consume(inputItem, inputAmount),
				ForgeCookingInput.durabilityUse("ingotmold"),
				outputItem,
				1,
				DEFAULT_PROCESS_TIME
		));
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

	private static void registerSawBlade() {
		register(new ForgeCookingRecipe(
				"sawblade_from_ironbar",
				ForgeCookingInput.consume("ironbar", 2),
				ForgeCookingInput.durabilityUse(DSItemRegistry.sawBladeMoldStringID),
				DSItemRegistry.sawBladeStringID,
				1,
				DEFAULT_PROCESS_TIME
		));
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
		if (recipe.firstInput != null && recipe.firstInput.resultBehavior == ForgeCookingInput.ResultBehavior.CONSUME) {
			forgeWorkedIngredients.add(recipe.firstInput.itemStringID);
		}
		if (recipe.secondInput != null && recipe.secondInput.resultBehavior == ForgeCookingInput.ResultBehavior.CONSUME) {
			forgeWorkedIngredients.add(recipe.secondInput.itemStringID);
		}
		forgeWorkedIngredients.add(recipe.outputItemStringID);
	}

	public static boolean isForgeWorkedIngredient(String itemStringID) {
		return itemStringID != null && forgeWorkedIngredients.contains(itemStringID);
	}

	public static List<ForgeCookingRecipe> getRecipes() {
		return Collections.unmodifiableList(recipes);
	}
}
