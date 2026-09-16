package opusliews.crafting;

import necesse.engine.localization.message.StaticMessage;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemCategory;
import necesse.inventory.item.ItemCategoryManager;

public class CraftingComponentCategories {
	public static final ItemCategoryManager typeManager = new ItemCategoryManager(new ItemCategory());
	public static final ItemCategoryManager materialClassManager = new ItemCategoryManager(new ItemCategory());
	public static final ItemCategoryManager materialManager = new ItemCategoryManager(new ItemCategory());
	private static boolean registered;

	public static void register() {
		if (registered) return;
		typeManager.createCategory("000", new StaticMessage("Armor Panel"), "armorpanel");
		typeManager.createCategory("001", new StaticMessage("Blade"), "blade");
		typeManager.createCategory("002", new StaticMessage("Casing"), "casing");
		typeManager.createCategory("003", new StaticMessage("Focus"), "focus");
		typeManager.createCategory("004", new StaticMessage("Hinge"), "hinge");
		typeManager.createCategory("005", new StaticMessage("Hook"), "hook");
		typeManager.createCategory("006", new StaticMessage("Ingredient"), "ingredient");
		typeManager.createCategory("007", new StaticMessage("Mechanism"), "mechanism");
		typeManager.createCategory("008", new StaticMessage("Nail"), "nail");
		typeManager.createCategory("009", new StaticMessage("Pick Plate"), "pickplate");
		typeManager.createCategory("010", new StaticMessage("Plank"), "plank");
		typeManager.createCategory("011", new StaticMessage("Plate"), "plate");
		typeManager.createCategory("012", new StaticMessage("Shaft"), "shaft");
		typeManager.createCategory("013", new StaticMessage("String"), "string");
		materialClassManager.createCategory("000", new StaticMessage("Bone"), "bone");
		materialClassManager.createCategory("001", new StaticMessage("Gem"), "gem");
		materialClassManager.createCategory("002", new StaticMessage("Magic"), "magic");
		materialClassManager.createCategory("003", new StaticMessage("Metal"), "metal");
		materialClassManager.createCategory("004", new StaticMessage("Organic"), "organic");
		materialClassManager.createCategory("005", new StaticMessage("Textile"), "textile");
		materialClassManager.createCategory("006", new StaticMessage("Wood"), "wood");
		materialManager.createCategory("000", new StaticMessage("Amethyst"), "amethyst");
		materialManager.createCategory("001", new StaticMessage("Ancientfossil"), "ancientfossil");
		materialManager.createCategory("002", new StaticMessage("Arcanic"), "arcanic");
		materialManager.createCategory("003", new StaticMessage("Bamboo"), "bamboo");
		materialManager.createCategory("004", new StaticMessage("Birch"), "birch");
		materialManager.createCategory("005", new StaticMessage("Bloodplate"), "bloodplate");
		materialManager.createCategory("006", new StaticMessage("Bone"), "bone");
		materialManager.createCategory("007", new StaticMessage("Cloth"), "cloth");
		materialManager.createCategory("008", new StaticMessage("Copper"), "copper");
		materialManager.createCategory("009", new StaticMessage("Crystal"), "crystal");
		materialManager.createCategory("010", new StaticMessage("Deadwood"), "deadwood");
		materialManager.createCategory("011", new StaticMessage("Demonic"), "demonic");
		materialManager.createCategory("012", new StaticMessage("Dryad"), "dryad");
		materialManager.createCategory("013", new StaticMessage("Emerald"), "emerald");
		materialManager.createCategory("014", new StaticMessage("Feather"), "feather");
		materialManager.createCategory("015", new StaticMessage("Firemone"), "firemone");
		materialManager.createCategory("016", new StaticMessage("Glacial"), "glacial");
		materialManager.createCategory("017", new StaticMessage("Gold"), "gold");
		materialManager.createCategory("018", new StaticMessage("Grass"), "grass");
		materialManager.createCategory("019", new StaticMessage("Honeycomb"), "honeycomb");
		materialManager.createCategory("020", new StaticMessage("Iron"), "iron");
		materialManager.createCategory("021", new StaticMessage("Ivy"), "ivy");
		materialManager.createCategory("022", new StaticMessage("Magic"), "magic");
		materialManager.createCategory("023", new StaticMessage("Maple"), "maple");
		materialManager.createCategory("024", new StaticMessage("Metal"), "metal");
		materialManager.createCategory("025", new StaticMessage("Mycelium"), "mycelium");
		materialManager.createCategory("026", new StaticMessage("Nightsteel"), "nightsteel");
		materialManager.createCategory("027", new StaticMessage("Oak"), "oak");
		materialManager.createCategory("028", new StaticMessage("Palm"), "palm");
		materialManager.createCategory("029", new StaticMessage("Pine"), "pine");
		materialManager.createCategory("030", new StaticMessage("Quartz"), "quartz");
		materialManager.createCategory("031", new StaticMessage("Reinforced"), "reinforced");
		materialManager.createCategory("032", new StaticMessage("Ruby"), "ruby");
		materialManager.createCategory("033", new StaticMessage("Runic"), "runic");
		materialManager.createCategory("034", new StaticMessage("Slime"), "slime");
		materialManager.createCategory("035", new StaticMessage("Spider"), "spider");
		materialManager.createCategory("036", new StaticMessage("Spiderite"), "spiderite");
		materialManager.createCategory("037", new StaticMessage("Spruce"), "spruce");
		materialManager.createCategory("038", new StaticMessage("Tungsten"), "tungsten");
		materialManager.createCategory("039", new StaticMessage("Void"), "void");
		materialManager.createCategory("040", new StaticMessage("Willow"), "willow");
		materialManager.createCategory("041", new StaticMessage("Wooden"), "wooden");
		registered = true;
	}

	public static <T extends Item> T categorize(T item, String type, String materialClass, String material) {
		item.setItemCategory("materials");
		item.setItemCategory(ItemCategory.craftingManager, "materials");
		if (type != null) item.setItemCategory(typeManager, type);
		if (materialClass != null) item.setItemCategory(materialClassManager, materialClass);
		if (material != null) item.setItemCategory(materialManager, material);
		return item;
	}
}