package opusliews.crafting;

import necesse.engine.localization.message.LocalMessage;
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
		typeManager.createCategory("000", new LocalMessage("componenttype", "armorpanel"), "armorpanel");
		typeManager.createCategory("001", new LocalMessage("componenttype", "blade"), "blade");
		typeManager.createCategory("002", new LocalMessage("componenttype", "casing"), "casing");
		typeManager.createCategory("003", new LocalMessage("componenttype", "focus"), "focus");
		typeManager.createCategory("004", new LocalMessage("componenttype", "hinge"), "hinge");
		typeManager.createCategory("005", new LocalMessage("componenttype", "hook"), "hook");
		typeManager.createCategory("006", new LocalMessage("componenttype", "ingredient"), "ingredient");
		typeManager.createCategory("007", new LocalMessage("componenttype", "mechanism"), "mechanism");
		typeManager.createCategory("008", new LocalMessage("componenttype", "nail"), "nail");
		typeManager.createCategory("009", new LocalMessage("componenttype", "pickplate"), "pickplate");
		typeManager.createCategory("010", new LocalMessage("componenttype", "plank"), "plank");
		typeManager.createCategory("011", new LocalMessage("componenttype", "plate"), "plate");
		typeManager.createCategory("012", new LocalMessage("componenttype", "shaft"), "shaft");
		typeManager.createCategory("013", new LocalMessage("componenttype", "string"), "string");
		materialClassManager.createCategory("000", new LocalMessage("materialclass", "bone"), "bone");
		materialClassManager.createCategory("001", new LocalMessage("materialclass", "gem"), "gem");
		materialClassManager.createCategory("002", new LocalMessage("materialclass", "magic"), "magic");
		materialClassManager.createCategory("003", new LocalMessage("materialclass", "metal"), "metal");
		materialClassManager.createCategory("004", new LocalMessage("materialclass", "organic"), "organic");
		materialClassManager.createCategory("005", new LocalMessage("materialclass", "textile"), "textile");
		materialClassManager.createCategory("006", new LocalMessage("materialclass", "wood"), "wood");
		materialManager.createCategory("000", new LocalMessage("material", "amethyst"), "amethyst");
		materialManager.createCategory("001", new LocalMessage("material", "ancientfossil"), "ancientfossil");
		materialManager.createCategory("002", new LocalMessage("material", "arcanic"), "arcanic");
		materialManager.createCategory("003", new LocalMessage("material", "bamboo"), "bamboo");
		materialManager.createCategory("004", new LocalMessage("material", "birch"), "birch");
		materialManager.createCategory("005", new LocalMessage("material", "bloodplate"), "bloodplate");
		materialManager.createCategory("006", new LocalMessage("material", "bone"), "bone");
		materialManager.createCategory("007", new LocalMessage("material", "cloth"), "cloth");
		materialManager.createCategory("008", new LocalMessage("material", "copper"), "copper");
		materialManager.createCategory("009", new LocalMessage("material", "crystal"), "crystal");
		materialManager.createCategory("010", new LocalMessage("material", "deadwood"), "deadwood");
		materialManager.createCategory("011", new LocalMessage("material", "demonic"), "demonic");
		materialManager.createCategory("012", new LocalMessage("material", "dryad"), "dryad");
		materialManager.createCategory("013", new LocalMessage("material", "emerald"), "emerald");
		materialManager.createCategory("014", new LocalMessage("material", "feather"), "feather");
		materialManager.createCategory("015", new LocalMessage("material", "firemone"), "firemone");
		materialManager.createCategory("016", new LocalMessage("material", "glacial"), "glacial");
		materialManager.createCategory("017", new LocalMessage("material", "gold"), "gold");
		materialManager.createCategory("018", new LocalMessage("material", "grass"), "grass");
		materialManager.createCategory("019", new LocalMessage("material", "honeycomb"), "honeycomb");
		materialManager.createCategory("020", new LocalMessage("material", "iron"), "iron");
		materialManager.createCategory("021", new LocalMessage("material", "ivy"), "ivy");
		materialManager.createCategory("022", new LocalMessage("material", "magic"), "magic");
		materialManager.createCategory("023", new LocalMessage("material", "maple"), "maple");
		materialManager.createCategory("024", new LocalMessage("material", "metal"), "metal");
		materialManager.createCategory("025", new LocalMessage("material", "mycelium"), "mycelium");
		materialManager.createCategory("026", new LocalMessage("material", "nightsteel"), "nightsteel");
		materialManager.createCategory("027", new LocalMessage("material", "oak"), "oak");
		materialManager.createCategory("028", new LocalMessage("material", "palm"), "palm");
		materialManager.createCategory("029", new LocalMessage("material", "pine"), "pine");
		materialManager.createCategory("030", new LocalMessage("material", "quartz"), "quartz");
		materialManager.createCategory("031", new LocalMessage("material", "reinforced"), "reinforced");
		materialManager.createCategory("032", new LocalMessage("material", "ruby"), "ruby");
		materialManager.createCategory("033", new LocalMessage("material", "runic"), "runic");
		materialManager.createCategory("034", new LocalMessage("material", "slime"), "slime");
		materialManager.createCategory("035", new LocalMessage("material", "spider"), "spider");
		materialManager.createCategory("036", new LocalMessage("material", "spiderite"), "spiderite");
		materialManager.createCategory("037", new LocalMessage("material", "spruce"), "spruce");
		materialManager.createCategory("038", new LocalMessage("material", "tungsten"), "tungsten");
		materialManager.createCategory("039", new LocalMessage("material", "void"), "void");
		materialManager.createCategory("040", new LocalMessage("material", "willow"), "willow");
		materialManager.createCategory("041", new LocalMessage("material", "wooden"), "wooden");
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