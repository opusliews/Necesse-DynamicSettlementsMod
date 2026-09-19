package opusliews;

import necesse.engine.localization.message.StaticMessage;
import necesse.engine.registries.GlobalIngredientRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.item.Item;
import necesse.inventory.item.matItem.MatItem;
import opusliews.armor.BuilderBootsArmorItem;
import opusliews.armor.BuilderHatArmorItem;
import opusliews.armor.BuilderShirtArmorItem;
import opusliews.crafting.CraftingComponentCategories;
import opusliews.crafting.CraftingMaterialItem;
import opusliews.item.BlueprintItem;
import opusliews.item.InspectionGlassItem;
import opusliews.item.MalignanceGogglesItem;
import opusliews.item.ProjectEraserItem;
import opusliews.item.TreasureShovelItem;

public class DSItemRegistry {
	public static final String featherStringID = "feather";
	public static final String groundFiremoneStringID = "groundfiremone";
	public static final String honeycombFragmentStringID = "honeycombfragment";
	public static final String waxStringID = "wax";
	public static final String oakplankStringID = "oakplank";
	public static final String nailStringID = "nail";
	public static final String hingeStringID = "hinge";
	public static final String spruceplankStringID = "spruceplank";
	public static final String pineplankStringID = "pineplank";
	public static final String palmplankStringID = "palmplank";
	public static final String birchplankStringID = "birchplank";
	public static final String willowplankStringID = "willowplank";
	public static final String mapleplankStringID = "mapleplank";
	public static final String deadwoodplankStringID = "deadwoodplank";
	public static final String bambooplankStringID = "bambooplank";
	public static final String dryadplankStringID = "dryadplank";
	public static final String woodenshaftStringID = "woodenshaft";
	public static final String coppershaftStringID = "coppershaft";
	public static final String ironshaftStringID = "ironshaft";
	public static final String goldshaftStringID = "goldshaft";
	public static final String tungstenshaftStringID = "tungstenshaft";
	public static final String demonicshaftStringID = "demonicshaft";
	public static final String ivyshaftStringID = "ivyshaft";
	public static final String quartzshaftStringID = "quartzshaft";
	public static final String slimeshaftStringID = "slimeshaft";
	public static final String glacialshaftStringID = "glacialshaft";
	public static final String dryadshaftStringID = "dryadshaft";
	public static final String myceliumshaftStringID = "myceliumshaft";
	public static final String spideriteshaftStringID = "spideriteshaft";
	public static final String arcanicshaftStringID = "arcanicshaft";
	public static final String voidshaftStringID = "voidshaft";
	public static final String copperplateStringID = "copperplate";
	public static final String ironplateStringID = "ironplate";
	public static final String goldplateStringID = "goldplate";
	public static final String tungstenplateStringID = "tungstenplate";
	public static final String demonicplateStringID = "demonicplate";
	public static final String ivyplateStringID = "ivyplate";
	public static final String quartzplateStringID = "quartzplate";
	public static final String slimeplateStringID = "slimeplate";
	public static final String glacialplateStringID = "glacialplate";
	public static final String dryadplateStringID = "dryadplate";
	public static final String myceliumplateStringID = "myceliumplate";
	public static final String runicplateStringID = "runicplate";
	public static final String spideriteplateStringID = "spideriteplate";
	public static final String nightsteelplateStringID = "nightsteelplate";
	public static final String ancientfossilplateStringID = "ancientfossilplate";
	public static final String arcanicplateStringID = "arcanicplate";
	public static final String crystalplateStringID = "crystalplate";
	public static final String emeraldplateStringID = "emeraldplate";
	public static final String rubyplateStringID = "rubyplate";
	public static final String amethystplateStringID = "amethystplate";
	public static final String bloodplateplateStringID = "bloodplateplate";
	public static final String spiderplateStringID = "spiderplate";
	public static final String voidplateStringID = "voidplate";
	public static final String woodenbladeStringID = "woodenblade";
	public static final String copperbladeStringID = "copperblade";
	public static final String ironbladeStringID = "ironblade";
	public static final String goldbladeStringID = "goldblade";
	public static final String tungstenbladeStringID = "tungstenblade";
	public static final String demonicbladeStringID = "demonicblade";
	public static final String ivybladeStringID = "ivyblade";
	public static final String quartzbladeStringID = "quartzblade";
	public static final String glacialbladeStringID = "glacialblade";
	public static final String slimebladeStringID = "slimeblade";
	public static final String amethystbladeStringID = "amethystblade";
	public static final String stringStringID = "string";
	public static final String clothpanelStringID = "clothpanel";
	public static final String bonepanelStringID = "bonepanel";
	public static final String slimearmorpanelStringID = "slimearmorpanel";
	public static final String dryadarmorpanelStringID = "dryadarmorpanel";
	public static final String runicarmorpanelStringID = "runicarmorpanel";
	public static final String hookStringID = "metalhook";
	public static final String metalcasingStringID = "metalcasing";
	public static final String clockworkmechanismStringID = "clockworkmechanism";
	public static final String magicfocusStringID = "magicfocus";
	public static final String unfiredBrickStringID = "unfiredbrick";
	public static final String brickStringID = "brick";

	public static void registerItems() {
		registerGlobalGroups();
		CraftingComponentCategories.register();

		ItemRegistry.registerItem("builderhat", new BuilderHatArmorItem(), 50.0F, true);
		ItemRegistry.registerItem("buildershirt", new BuilderShirtArmorItem(), 50.0F, true);
		ItemRegistry.registerItem("builderboots", new BuilderBootsArmorItem(), 50.0F, true);
		ItemRegistry.registerItem("blueprintItem", new BlueprintItem(), 25.0F, true);
		ItemRegistry.registerItem("projecteraser", new ProjectEraserItem(), 30.0F, true);
		ItemRegistry.registerItem("inspectionglass", new InspectionGlassItem(), 20.0F, true);
		ItemRegistry.registerItem("malignancegoggles", new MalignanceGogglesItem(), 100.0F, true);
		ItemRegistry.registerItem(TreasureShovelItem.stringID, new TreasureShovelItem(), 200.0F, true);

		registerMaterial(featherStringID, new MatItem(500, Item.Rarity.NORMAL), 3.0F, "ingredient", "organic", "feather");
		registerMaterial(groundFiremoneStringID, new MatItem(500, Item.Rarity.NORMAL), 8.0F, "ingredient", "organic", "firemone");
		registerMaterial(honeycombFragmentStringID, new MatItem(500, Item.Rarity.NORMAL), 5.0F, "ingredient", "organic", "honeycomb");
		registerMaterial(waxStringID, new MatItem(500, Item.Rarity.NORMAL), 6.0F, "ingredient", "organic", "honeycomb");

		registerMaterial(oakplankStringID, new CraftingMaterialItem("Oak Plank", 500, Item.Rarity.NORMAL, "dsanyplank"), 5.0F, "plank", "wood", "oak");
		registerMaterial(nailStringID, new CraftingMaterialItem("Nail", 500, Item.Rarity.NORMAL), 5.0F, "nail", "metal", "metal");
		registerMaterial(hingeStringID, new CraftingMaterialItem("Hinge", 500, Item.Rarity.NORMAL), 5.0F, "hinge", "metal", "metal");
		registerMaterial(spruceplankStringID, new CraftingMaterialItem("Spruce Plank", 500, Item.Rarity.NORMAL, "dsanyplank"), 5.0F, "plank", "wood", "spruce");
		registerMaterial(pineplankStringID, new CraftingMaterialItem("Pine Plank", 500, Item.Rarity.NORMAL, "dsanyplank"), 5.0F, "plank", "wood", "pine");
		registerMaterial(palmplankStringID, new CraftingMaterialItem("Palm Plank", 500, Item.Rarity.NORMAL, "dsanyplank"), 5.0F, "plank", "wood", "palm");
		registerMaterial(birchplankStringID, new CraftingMaterialItem("Birch Plank", 500, Item.Rarity.NORMAL, "dsanyplank"), 5.0F, "plank", "wood", "birch");
		registerMaterial(willowplankStringID, new CraftingMaterialItem("Willow Plank", 500, Item.Rarity.NORMAL, "dsanyplank"), 5.0F, "plank", "wood", "willow");
		registerMaterial(mapleplankStringID, new CraftingMaterialItem("Maple Plank", 500, Item.Rarity.NORMAL, "dsanyplank"), 5.0F, "plank", "wood", "maple");
		registerMaterial(deadwoodplankStringID, new CraftingMaterialItem("Deadwood Plank", 500, Item.Rarity.NORMAL, "dsanyplank"), 5.0F, "plank", "wood", "deadwood");
		registerMaterial(bambooplankStringID, new CraftingMaterialItem("Bamboo Plank", 500, Item.Rarity.NORMAL, "dsanyplank"), 5.0F, "plank", "wood", "bamboo");
		registerMaterial(dryadplankStringID, new CraftingMaterialItem("Dryad Plank", 500, Item.Rarity.NORMAL, "dsanyplank"), 5.0F, "plank", "wood", "dryad");
		registerMaterial(woodenshaftStringID, new CraftingMaterialItem("Wooden Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft"), 5.0F, "shaft", "wood", "wooden");
		registerMaterial(coppershaftStringID, new CraftingMaterialItem("Copper Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft", "dsanymetalshaft"), 5.0F, "shaft", "metal", "copper");
		registerMaterial(ironshaftStringID, new CraftingMaterialItem("Iron Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft", "dsanymetalshaft"), 5.0F, "shaft", "metal", "iron");
		registerMaterial(goldshaftStringID, new CraftingMaterialItem("Gold Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft", "dsanymetalshaft"), 5.0F, "shaft", "metal", "gold");
		registerMaterial(tungstenshaftStringID, new CraftingMaterialItem("Tungsten Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft", "dsanymetalshaft"), 5.0F, "shaft", "metal", "tungsten");
		registerMaterial(demonicshaftStringID, new CraftingMaterialItem("Demonic Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft", "dsanymetalshaft"), 5.0F, "shaft", "metal", "demonic");
		registerMaterial(ivyshaftStringID, new CraftingMaterialItem("Ivy Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft"), 5.0F, "shaft", "organic", "ivy");
		registerMaterial(quartzshaftStringID, new CraftingMaterialItem("Quartz Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft"), 5.0F, "shaft", "gem", "quartz");
		registerMaterial(slimeshaftStringID, new CraftingMaterialItem("Slime Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft"), 5.0F, "shaft", "organic", "slime");
		registerMaterial(glacialshaftStringID, new CraftingMaterialItem("Glacial Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft", "dsanymetalshaft"), 5.0F, "shaft", "metal", "glacial");
		registerMaterial(dryadshaftStringID, new CraftingMaterialItem("Dryad Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft"), 5.0F, "shaft", "wood", "dryad");
		registerMaterial(myceliumshaftStringID, new CraftingMaterialItem("Mycelium Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft"), 5.0F, "shaft", "organic", "mycelium");
		registerMaterial(spideriteshaftStringID, new CraftingMaterialItem("Spiderite Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft", "dsanymetalshaft"), 5.0F, "shaft", "metal", "spiderite");
		registerMaterial(arcanicshaftStringID, new CraftingMaterialItem("Arcanic Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft"), 5.0F, "shaft", "magic", "arcanic");
		registerMaterial(voidshaftStringID, new CraftingMaterialItem("Void Shaft", 500, Item.Rarity.NORMAL, "dsanyshaft"), 5.0F, "shaft", "magic", "void");
		registerMaterial(copperplateStringID, new CraftingMaterialItem("Copper Plate", 500, Item.Rarity.NORMAL, "dsanyplate", "dsanymetalplate"), 5.0F, "plate", "metal", "copper");
		registerMaterial(ironplateStringID, new CraftingMaterialItem("Iron Plate", 500, Item.Rarity.NORMAL, "dsanyplate", "dsanymetalplate"), 5.0F, "plate", "metal", "iron");
		registerMaterial(goldplateStringID, new CraftingMaterialItem("Gold Plate", 500, Item.Rarity.NORMAL, "dsanyplate", "dsanymetalplate"), 5.0F, "plate", "metal", "gold");
		registerMaterial(tungstenplateStringID, new CraftingMaterialItem("Tungsten Plate", 500, Item.Rarity.NORMAL, "dsanyplate", "dsanymetalplate"), 5.0F, "plate", "metal", "tungsten");
		registerMaterial(demonicplateStringID, new CraftingMaterialItem("Demonic Plate", 500, Item.Rarity.NORMAL, "dsanyplate", "dsanymetalplate"), 5.0F, "plate", "metal", "demonic");
		registerMaterial(ivyplateStringID, new CraftingMaterialItem("Ivy Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "organic", "ivy");
		registerMaterial(quartzplateStringID, new CraftingMaterialItem("Quartz Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "gem", "quartz");
		registerMaterial(slimeplateStringID, new CraftingMaterialItem("Slime Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "organic", "slime");
		registerMaterial(glacialplateStringID, new CraftingMaterialItem("Glacial Plate", 500, Item.Rarity.NORMAL, "dsanyplate", "dsanymetalplate"), 5.0F, "plate", "metal", "glacial");
		registerMaterial(dryadplateStringID, new CraftingMaterialItem("Dryad Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "wood", "dryad");
		registerMaterial(myceliumplateStringID, new CraftingMaterialItem("Mycelium Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "organic", "mycelium");
		registerMaterial(runicplateStringID, new CraftingMaterialItem("Runic Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "magic", "runic");
		registerMaterial(spideriteplateStringID, new CraftingMaterialItem("Spiderite Plate", 500, Item.Rarity.NORMAL, "dsanyplate", "dsanymetalplate"), 5.0F, "plate", "metal", "spiderite");
		registerMaterial(nightsteelplateStringID, new CraftingMaterialItem("Nightsteel Plate", 500, Item.Rarity.NORMAL, "dsanyplate", "dsanymetalplate"), 5.0F, "plate", "metal", "nightsteel");
		registerMaterial(ancientfossilplateStringID, new CraftingMaterialItem("Ancient Fossil Plate", 500, Item.Rarity.NORMAL, "dsanyplate", "dsanymetalplate"), 5.0F, "plate", "metal", "ancientfossil");
		registerMaterial(arcanicplateStringID, new CraftingMaterialItem("Arcanic Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "magic", "arcanic");
		registerMaterial(crystalplateStringID, new CraftingMaterialItem("Crystal Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "gem", "crystal");
		registerMaterial(emeraldplateStringID, new CraftingMaterialItem("Emerald Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "gem", "emerald");
		registerMaterial(rubyplateStringID, new CraftingMaterialItem("Ruby Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "gem", "ruby");
		registerMaterial(amethystplateStringID, new CraftingMaterialItem("Amethyst Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "gem", "amethyst");
		registerMaterial(bloodplateplateStringID, new CraftingMaterialItem("Bloodplate Plate", 500, Item.Rarity.NORMAL, "dsanyplate", "dsanymetalplate"), 5.0F, "plate", "metal", "bloodplate");
		registerMaterial(spiderplateStringID, new CraftingMaterialItem("Spider Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "organic", "spider");
		registerMaterial(voidplateStringID, new CraftingMaterialItem("Void Plate", 500, Item.Rarity.NORMAL, "dsanyplate"), 5.0F, "plate", "magic", "void");
		registerMaterial(woodenbladeStringID, new CraftingMaterialItem("Wooden Blade", 500, Item.Rarity.NORMAL, "dsanyblade"), 5.0F, "blade", "wood", "wooden");
		registerMaterial(copperbladeStringID, new CraftingMaterialItem("Copper Blade", 500, Item.Rarity.NORMAL, "dsanyblade", "dsanymetalblade"), 5.0F, "blade", "metal", "copper");
		registerMaterial(ironbladeStringID, new CraftingMaterialItem("Iron Blade", 500, Item.Rarity.NORMAL, "dsanyblade", "dsanymetalblade"), 5.0F, "blade", "metal", "iron");
		registerMaterial(goldbladeStringID, new CraftingMaterialItem("Gold Blade", 500, Item.Rarity.NORMAL, "dsanyblade", "dsanymetalblade"), 5.0F, "blade", "metal", "gold");
		registerMaterial(tungstenbladeStringID, new CraftingMaterialItem("Tungsten Blade", 500, Item.Rarity.NORMAL, "dsanyblade", "dsanymetalblade"), 5.0F, "blade", "metal", "tungsten");
		registerMaterial(demonicbladeStringID, new CraftingMaterialItem("Demonic Blade", 500, Item.Rarity.NORMAL, "dsanyblade", "dsanymetalblade"), 5.0F, "blade", "metal", "demonic");
		registerMaterial(ivybladeStringID, new CraftingMaterialItem("Ivy Blade", 500, Item.Rarity.NORMAL, "dsanyblade"), 5.0F, "blade", "organic", "ivy");
		registerMaterial(quartzbladeStringID, new CraftingMaterialItem("Quartz Blade", 500, Item.Rarity.NORMAL, "dsanyblade"), 5.0F, "blade", "gem", "quartz");
		registerMaterial(glacialbladeStringID, new CraftingMaterialItem("Glacial Blade", 500, Item.Rarity.NORMAL, "dsanyblade", "dsanymetalblade"), 5.0F, "blade", "metal", "glacial");
		registerMaterial(slimebladeStringID, new CraftingMaterialItem("Slime Blade", 500, Item.Rarity.NORMAL, "dsanyblade"), 5.0F, "blade", "organic", "slime");
		registerMaterial(amethystbladeStringID, new CraftingMaterialItem("Amethyst Blade", 500, Item.Rarity.NORMAL, "dsanyblade"), 5.0F, "blade", "gem", "amethyst");
		registerMaterial(stringStringID, new CraftingMaterialItem("String", 500, Item.Rarity.NORMAL), 5.0F, "string", "textile", "grass");
		registerMaterial(clothpanelStringID, new CraftingMaterialItem("Cloth Panel", 500, Item.Rarity.NORMAL, "dsanyarmorpanel"), 5.0F, "armorpanel", "textile", "cloth");
		registerMaterial(bonepanelStringID, new CraftingMaterialItem("Bone Panel", 500, Item.Rarity.NORMAL, "dsanyarmorpanel"), 5.0F, "armorpanel", "bone", "bone");
		registerMaterial(slimearmorpanelStringID, new CraftingMaterialItem("Slime Armor Panel", 500, Item.Rarity.NORMAL, "dsanyarmorpanel"), 5.0F, "armorpanel", "organic", "slime");
		registerMaterial(dryadarmorpanelStringID, new CraftingMaterialItem("Dryad Armor Panel", 500, Item.Rarity.NORMAL, "dsanyarmorpanel"), 5.0F, "armorpanel", "wood", "dryad");
		registerMaterial(runicarmorpanelStringID, new CraftingMaterialItem("Runic Armor Panel", 500, Item.Rarity.NORMAL, "dsanyarmorpanel"), 5.0F, "armorpanel", "magic", "runic");
		registerMaterial(hookStringID, new CraftingMaterialItem("Hook", 500, Item.Rarity.NORMAL), 5.0F, "hook", "metal", "metal");
		registerMaterial(metalcasingStringID, new CraftingMaterialItem("Metal Casing", 500, Item.Rarity.NORMAL), 5.0F, "casing", "metal", "metal");
		registerMaterial(clockworkmechanismStringID, new CraftingMaterialItem("Clockwork Mechanism", 500, Item.Rarity.NORMAL), 5.0F, "mechanism", "metal", "metal");
		registerMaterial(magicfocusStringID, new CraftingMaterialItem("Magic Focus", 500, Item.Rarity.NORMAL), 5.0F, "focus", "magic", "magic");
		registerMaterial(unfiredBrickStringID, new MatItem(500, Item.Rarity.NORMAL), 1.0F, null, null, null);
		registerMaterial(brickStringID, new MatItem(500, Item.Rarity.NORMAL), 2.0F, null, null, null);
	}

	private static void registerGlobalGroups() {
		registerGlobal("dsanyplank", "Any Plank");
		registerGlobal("dsanyshaft", "Any Shaft");
		registerGlobal("dsanymetalshaft", "Any Metal Shaft");
		registerGlobal("dsanyplate", "Any Plate");
		registerGlobal("dsanymetalplate", "Any Metal Plate");
		registerGlobal("dsanyblade", "Any Blade");
		registerGlobal("dsanymetalblade", "Any Metal Blade");
		registerGlobal("dsanyarmorpanel", "Any Armor Panel");
	}

	private static void registerGlobal(String id, String name) {
		GlobalIngredientRegistry.registerGlobalIngredient(id, new StaticMessage(name), null);
	}

	private static void registerMaterial(String id, MatItem item, float brokerValue, String type, String materialClass, String material) {
		CraftingComponentCategories.categorize(item, emptyToNull(type), emptyToNull(materialClass), emptyToNull(material));
		ItemRegistry.registerItem(id, item, brokerValue, true);
	}

	private static String emptyToNull(String value) {
		return value == null || value.isEmpty() ? null : value;
	}
}
