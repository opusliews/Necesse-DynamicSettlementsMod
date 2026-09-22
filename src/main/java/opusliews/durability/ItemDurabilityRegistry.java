package opusliews.durability;

import necesse.inventory.item.Item;
import opusliews.logging.Logging;

import java.util.HashMap;
import java.util.Map;

public final class ItemDurabilityRegistry {
	private static final Map<String, ItemDurabilityDefinition> definitions = new HashMap<>();

	private ItemDurabilityRegistry() {
	}

	public static ItemDurabilityDefinition configure(String itemStringID, int durability) {
		ItemDurabilityDefinition definition = new ItemDurabilityDefinition(true, durability);
		definitions.put(itemStringID, definition);
		Logging.logMessage("[Durability] Registered item=" + itemStringID + " maxDurability=" + durability);
		return definition;
	}

	public static ItemDurabilityDefinition configure(Item item, int durability) {
		return configure(item.getStringID(), durability);
	}

	public static void setUnbreakable(String itemStringID) {
		definitions.remove(itemStringID);
	}

	public static void setUnbreakable(Item item) {
		setUnbreakable(item.getStringID());
	}

	public static ItemDurabilityDefinition get(Item item) {
		if (item == null) return null;
		return definitions.get(item.getStringID());
	}

	public static boolean isBreakable(Item item) {
		ItemDurabilityDefinition definition = get(item);
		return definition != null && definition.breakable && definition.durability > 0;
	}

	public static int getMaxDurability(Item item) {
		ItemDurabilityDefinition definition = get(item);
		return definition == null || !definition.breakable ? 0 : definition.durability;
	}

	public static void registerDurability() {
		registerCraftingMold("ingotmold");
		registerCraftingMold("pickaxeheadmold");
		registerCraftingMold("axeheadmold");
		registerCraftingMold("shovelheadmold");
		registerCraftingMold("sickleblademold");
		registerCraftingMold("shearsblademold");
		registerCraftingMold("swordblademold");
		registerCraftingMold("thickplatemold");

		registerBrokenTool("brokencoppertool");
		registerBrokenTool("brokenirontool");
	}

	private static void registerCraftingMold(String itemStringID) {
		ItemDurabilityRegistry.configure(itemStringID, 10)
				.on(DurabilityAction.CRAFTING_USE, 1)
				.destroyOnBreak();
	}

	private static void registerBrokenTool(String itemStringID) {
		ItemDurabilityRegistry.configure(itemStringID, 2)
				.on(DurabilityAction.CRAFTING_USE, 1)
				.destroyOnBreak();
	}
}
