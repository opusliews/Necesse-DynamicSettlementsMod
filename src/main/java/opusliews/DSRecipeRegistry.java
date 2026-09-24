package opusliews;

import necesse.engine.registries.RecipeTechRegistry;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.Recipes;
import necesse.inventory.recipe.Tech;
import opusliews.earlygame.CrudeWorkbenchFeature;
import opusliews.item.CrudeAxeItem;
import opusliews.item.FirestarterItem;
import opusliews.item.SharpenedStoneItem;
import opusliews.object.*;

import static opusliews.DSItemRegistry.*;

public class DSRecipeRegistry {
	public static void registerRecipes() {
		registerModRecipes();
		registerVanillaRecipes();
		registerCraftingMaterialRecipes();
	}

	private static void registerModRecipes() {
		register(FirestarterItem.stringID, 1, CrudeWorkbenchFeature.tech,
				ingredient(stringStringID, 1),
				ingredient("dsanyplank", 1),
				ingredient(woodenshaftStringID, 1));

		register(CrudeAxeItem.stringID, 1, CrudeWorkbenchFeature.tech,
				ingredient(stringStringID, 4),
				ingredient(woodenshaftStringID, 1),
				ingredient(SharpenedStoneItem.stringID, 1));

		register(FirestarterItem.stringID, 1, RecipeTechRegistry.WORKSTATION,
				ingredient(stringStringID, 1),
				ingredient("dsanyplank", 1),
				ingredient(woodenshaftStringID, 1));

		register(CrudeAxeItem.stringID, 1, RecipeTechRegistry.WORKSTATION,
				ingredient(stringStringID, 4),
				ingredient(woodenshaftStringID, 1),
				ingredient(SharpenedStoneItem.stringID, 1));

		register(unfiredBrickStringID, 1, CrudeWorkbenchFeature.tech, ingredient("clay", 1));
		register(unfiredBrickStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("clay", 1));

		register(unfiredIngotMoldStringID, 1, CrudeWorkbenchFeature.tech, ingredient("clay", 1));
		register(unfiredIngotMoldStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("clay", 1));

		register(unfiredPickaxeHeadMoldStringID, 1, CrudeWorkbenchFeature.tech, ingredient("clay", 1));
		register(unfiredPickaxeHeadMoldStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("clay", 1));

		register(unfiredAxeHeadMoldStringID, 1, CrudeWorkbenchFeature.tech, ingredient("clay", 1));
		register(unfiredAxeHeadMoldStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("clay", 1));

		register(unfiredShovelHeadMoldStringID, 1, CrudeWorkbenchFeature.tech, ingredient("clay", 1));
		register(unfiredShovelHeadMoldStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("clay", 1));

		register(unfiredSickleBladeMoldStringID, 1, CrudeWorkbenchFeature.tech, ingredient("clay", 1));
		register(unfiredSickleBladeMoldStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("clay", 1));

		register(unfiredShearsBladeMoldStringID, 1, CrudeWorkbenchFeature.tech, ingredient("clay", 1));
		register(unfiredShearsBladeMoldStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("clay", 1));

		register(unfiredSwordBladeMoldStringID, 1, CrudeWorkbenchFeature.tech, ingredient("clay", 1));
		register(unfiredSwordBladeMoldStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("clay", 1));

		register(unfiredThickPlateMoldStringID, 1, CrudeWorkbenchFeature.tech, ingredient("clay", 1));
		register(unfiredThickPlateMoldStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("clay", 1));

		register("blueprintItem", 1, RecipeTechRegistry.WORKSTATION, ingredient("stackofpaper", 1), ingredient("quillandparchment", 1));
		register("blueprintworkstation", 1, RecipeTechRegistry.WORKSTATION, ingredient("anylog", 15), ingredient("tungstenbar", 3), ingredient("stackofpaper", 1));
		register("builderhat", 1, RecipeTechRegistry.WORKSTATION, ingredient("wool", 12), ingredient("ironbar", 1));
		register("buildershirt", 1, RecipeTechRegistry.WORKSTATION, ingredient("wool", 16));
		register("builderboots", 1, RecipeTechRegistry.WORKSTATION, ingredient("wool", 8), ingredient("leather", 1));
		register("projecteraser", 1, RecipeTechRegistry.WORKSTATION, ingredient("quillandparchment", 1), ingredient("ironbar", 1));
		register("inspectionglass", 1, RecipeTechRegistry.WORKSTATION, ingredient("glass", 2), ingredient("ironbar", 1));
		register("malignancegoggles", 1, RecipeTechRegistry.DEMONIC_WORKSTATION, ingredient("inspectionglass", 2), ingredient("demonicbar", 1));
		register(BuilderJobRequestBulletinObject.stringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("stackofpaper", 1), ingredient("quillandparchment", 1));
		register(WarningBellObject.stringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("goldbar", 3), ingredient("ironbar", 1));

		register(CrudeTorchObject.stringID, 4, RecipeTechRegistry.NONE,
				ingredient(woodenshaftStringID, 1),
				ingredient("firemone", 1),
				ingredient("grass", 1));
	}

	private static void registerVanillaRecipes() {
		register("brickwall", 1, RecipeTechRegistry.WORKSTATION, ingredient(brickStringID, 4), ingredient("demonicbar", 1));
		register("torch", 4, RecipeTechRegistry.WORKSTATION, ingredient(woodenshaftStringID, 1), ingredient("charcoal", 1), ingredient("firemone", 1));

		register("woodshovel", 1, CrudeWorkbenchFeature.tech, ingredient("dsanyplank", 1));
		register("woodshovel", 1, RecipeTechRegistry.WORKSTATION, ingredient("dsanyplank", 1));
		register("woodsword", 1, CrudeWorkbenchFeature.tech, ingredient("dsanyplank", 1));
		register("woodsword", 1, RecipeTechRegistry.WORKSTATION, ingredient("dsanyplank", 1));
		register("forge", 1, CrudeWorkbenchFeature.tech, ingredient("brick", 1));
		register("forge", 1, RecipeTechRegistry.WORKSTATION, ingredient("brick", 1));
	}

	private static void registerCraftingMaterialRecipes() {
		// Planks
		register(oakplankStringID, 4, RecipeTechRegistry.CARPENTER, ingredient("oaklog", 1));
		register(spruceplankStringID, 4, RecipeTechRegistry.CARPENTER, ingredient("sprucelog", 1));
		register(pineplankStringID, 4, RecipeTechRegistry.CARPENTER, ingredient("pinelog", 1));
		register(palmplankStringID, 4, RecipeTechRegistry.CARPENTER, ingredient("palmlog", 1));
		register(birchplankStringID, 4, RecipeTechRegistry.CARPENTER, ingredient("birchlog", 1));
		register(willowplankStringID, 4, RecipeTechRegistry.CARPENTER, ingredient("willowlog", 1));
		register(mapleplankStringID, 4, RecipeTechRegistry.CARPENTER, ingredient("maplelog", 1));
		register(deadwoodplankStringID, 4, RecipeTechRegistry.CARPENTER, ingredient("deadwoodlog", 1));
		register(bambooplankStringID, 4, RecipeTechRegistry.CARPENTER, ingredient("bamboo", 1));
		register(dryadplankStringID, 4, RecipeTechRegistry.TUNGSTEN_CARPENTER, ingredient("dryadlog", 1));

		// Basic hardware
		register(nailStringID, 8, RecipeTechRegistry.IRON_ANVIL, ingredient("ironshaft", 1));
		register(hingeStringID, 8, RecipeTechRegistry.IRON_ANVIL, ingredient("ironplate", 1));
		register(hookStringID, 4, RecipeTechRegistry.IRON_ANVIL, ingredient("ironplate", 1));

		// Shafts
		register(woodenshaftStringID, 2, RecipeTechRegistry.CARPENTER, ingredient("dsanyplank", 1));
		register(coppershaftStringID, 2, RecipeTechRegistry.IRON_ANVIL, ingredient("copperbar", 1));
		register(ironshaftStringID, 2, RecipeTechRegistry.IRON_ANVIL, ingredient("ironbar", 1));
		register(goldshaftStringID, 2, RecipeTechRegistry.IRON_ANVIL, ingredient("goldbar", 1));
		register(tungstenshaftStringID, 2, RecipeTechRegistry.TUNGSTEN_ANVIL, ingredient("tungstenbar", 1));
		register(demonicshaftStringID, 2, RecipeTechRegistry.DEMONIC_ANVIL, ingredient("demonicbar", 1));
		register(ivyshaftStringID, 2, RecipeTechRegistry.DEMONIC_ANVIL, ingredient("ivybar", 1));
		register(quartzshaftStringID, 2, RecipeTechRegistry.DEMONIC_ANVIL, ingredient("quartz", 1));
		register(slimeshaftStringID, 2, RecipeTechRegistry.FALLEN_ANVIL, ingredient("slimematter", 1));
		register(glacialshaftStringID, 2, RecipeTechRegistry.TUNGSTEN_ANVIL, ingredient("glacialbar", 1));
		register(dryadshaftStringID, 2, RecipeTechRegistry.TUNGSTEN_CARPENTER, ingredient(dryadplankStringID, 1));
		register(myceliumshaftStringID, 2, RecipeTechRegistry.TUNGSTEN_ANVIL, ingredient("myceliumbar", 1));
		register(spideriteshaftStringID, 2, RecipeTechRegistry.FALLEN_ANVIL, ingredient("spideritebar", 1));
		register(arcanicshaftStringID, 2, RecipeTechRegistry.FALLEN_ANVIL, ingredient("electrifiedmana", 1));
		register(voidshaftStringID, 2, RecipeTechRegistry.DEMONIC_ANVIL, ingredient("demonicbar", 1), ingredient("voidshard", 1));

		// Plates
		register(copperplateStringID, 1, RecipeTechRegistry.IRON_ANVIL, ingredient("copperbar", 2));
		register(ironplateStringID, 1, RecipeTechRegistry.IRON_ANVIL, ingredient("ironbar", 2));
		register(goldplateStringID, 1, RecipeTechRegistry.IRON_ANVIL, ingredient("goldbar", 2));
		register(tungstenplateStringID, 1, RecipeTechRegistry.TUNGSTEN_ANVIL, ingredient("tungstenbar", 2));
		register(demonicplateStringID, 1, RecipeTechRegistry.DEMONIC_ANVIL, ingredient("demonicbar", 2));
		register(ivyplateStringID, 1, RecipeTechRegistry.DEMONIC_ANVIL, ingredient("ivybar", 2));
		register(quartzplateStringID, 1, RecipeTechRegistry.DEMONIC_ANVIL, ingredient("quartz", 2));
		register(slimeplateStringID, 1, RecipeTechRegistry.FALLEN_ANVIL, ingredient("slimematter", 2));
		register(glacialplateStringID, 1, RecipeTechRegistry.TUNGSTEN_ANVIL, ingredient("glacialbar", 2));
		register(dryadplateStringID, 1, RecipeTechRegistry.TUNGSTEN_CARPENTER, ingredient(dryadplankStringID, 2));
		register(myceliumplateStringID, 1, RecipeTechRegistry.TUNGSTEN_ANVIL, ingredient("myceliumbar", 2));
		register(runicplateStringID, 1, RecipeTechRegistry.DEMONIC_ANVIL, ingredient("runestone", 2), ingredient(demonicplateStringID, 1));
		register(spideriteplateStringID, 1, RecipeTechRegistry.FALLEN_ANVIL, ingredient("spideritebar", 2));
		register(nightsteelplateStringID, 1, RecipeTechRegistry.FALLEN_ANVIL, ingredient("nightsteelbar", 2));
		register(ancientfossilplateStringID, 1, RecipeTechRegistry.TUNGSTEN_ANVIL, ingredient("ancientfossilbar", 2));
		register(arcanicplateStringID, 1, RecipeTechRegistry.FALLEN_ANVIL, ingredient("electrifiedmana", 2), ingredient(demonicplateStringID, 1));
		register(crystalplateStringID, 1, RecipeTechRegistry.FALLEN_ANVIL, ingredient("omnicrystal", 2), ingredient(demonicplateStringID, 1));
		register(emeraldplateStringID, 1, RecipeTechRegistry.TUNGSTEN_ANVIL, ingredient("emerald", 2), ingredient(demonicplateStringID, 1));
		register(rubyplateStringID, 1, RecipeTechRegistry.TUNGSTEN_ANVIL, ingredient("ruby", 2), ingredient(demonicplateStringID, 1));
		register(amethystplateStringID, 1, RecipeTechRegistry.DEMONIC_ANVIL, ingredient("amethyst", 2), ingredient(demonicplateStringID, 1));
		register(bloodplateplateStringID, 1, RecipeTechRegistry.DEMONIC_ANVIL, ingredient(demonicplateStringID, 1), ingredient("batwing", 1));
		register(spiderplateStringID, 1, RecipeTechRegistry.FALLEN_ANVIL, ingredient("cavespidergland", 2), ingredient(spideriteplateStringID, 1));
		register(voidplateStringID, 1, RecipeTechRegistry.DEMONIC_ANVIL, ingredient(demonicplateStringID, 1), ingredient("voidshard", 2));

		// Blades
		register(woodenbladeStringID, 1, RecipeTechRegistry.CARPENTER, ingredient("dsanyplank", 3));
		register(slimebladeStringID, 1, RecipeTechRegistry.FALLEN_ANVIL, ingredient(slimeplateStringID, 1));

		// Armor panels
		register(clothpanelStringID, 1, RecipeTechRegistry.IRON_ANVIL, ingredient("wool", 4), ingredient("leather", 4));
		register(bonepanelStringID, 1, RecipeTechRegistry.TUNGSTEN_CARPENTER, ingredient("bone", 4), ingredient(clothpanelStringID, 1));
		register(slimearmorpanelStringID, 1, RecipeTechRegistry.FALLEN_ANVIL, ingredient("slimematter", 4), ingredient(clothpanelStringID, 1));
		register(dryadarmorpanelStringID, 1, RecipeTechRegistry.TUNGSTEN_CARPENTER, ingredient(dryadplankStringID, 4), ingredient(clothpanelStringID, 1));
		register(runicarmorpanelStringID, 1, RecipeTechRegistry.DEMONIC_ANVIL, ingredient(clothpanelStringID, 1), ingredient("runestone", 1));

		// Misc components
		register(metalcasingStringID, 10, RecipeTechRegistry.IRON_ANVIL, ingredient("ironbar", 1));
		register(clockworkmechanismStringID, 1, RecipeTechRegistry.IRON_ANVIL, ingredient("ironbar", 2));
		register(magicfocusStringID, 1, RecipeTechRegistry.ALCHEMY, ingredient("goldbar", 1), ingredient("manapotion", 1));
		register("clothscraps", 4, RecipeTechRegistry.WORKSTATION, ingredient("wool", 1));
		register(stringStringID, 1, CrudeWorkbenchFeature.tech, ingredient("grass", 4));
		register(stringStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("grass", 4));
		register(groundFiremoneStringID, 5, RecipeTechRegistry.GRAIN_MILL, ingredient("firemone", 1));
		register(waxStringID, 1, RecipeTechRegistry.COOKING_POT, ingredient(honeycombFragmentStringID, 1));


		// Tools
		register(TrapdoorObject.openStringID, 1, CrudeWorkbenchFeature.tech, ingredient("dsanyplank", 4));
		register(TrapdoorObject.openStringID, 1, RecipeTechRegistry.WORKSTATION, ingredient("dsanyplank", 4));
		register(HoleCaveLadderObject.stringID, 1, CrudeWorkbenchFeature.tech, ingredient(woodenshaftStringID, 32));
		register(HoleCaveLadderObject.stringID, 1, RecipeTechRegistry.WORKSTATION, ingredient(woodenshaftStringID, 32));
	}

	private static Ingredient ingredient(String stringID, int amount) {
		return new Ingredient(stringID, amount);
	}

	private static void register(String resultStringID, int resultAmount, Tech tech, Ingredient... ingredients) {
		Recipes.registerModRecipe(new Recipe(resultStringID, resultAmount, tech, ingredients));
	}
}
