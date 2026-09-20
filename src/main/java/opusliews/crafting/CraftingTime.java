package opusliews.crafting;

import java.util.IdentityHashMap;
import java.util.Map;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.item.Item;
import necesse.inventory.recipe.Recipe;

public final class CraftingTime {
	public static final long DEFAULT_TIME_MS = 2000L;

	private static final Map<Item, Long> itemTimes = new IdentityHashMap<>();

	private CraftingTime() {
	}

	public static <T extends Item> T set(T item, float seconds) {
		if (item == null) throw new IllegalArgumentException("item cannot be null");
		setMilliseconds(item, Math.round(seconds * 1000.0F));
		return item;
	}

	public static Item set(String itemStringID, float seconds) {
		Item item = ItemRegistry.getItem(itemStringID);
		if (item == null) throw new IllegalArgumentException("Unknown item: " + itemStringID);
		return set(item, seconds);
	}

	public static void setMilliseconds(Item item, long milliseconds) {
		if (item == null) throw new IllegalArgumentException("item cannot be null");
		if (milliseconds < 0L) throw new IllegalArgumentException("Crafting time cannot be negative");
		itemTimes.put(item, milliseconds);
	}

	public static void setMilliseconds(String itemStringID, long milliseconds) {
		Item item = ItemRegistry.getItem(itemStringID);
		if (item == null) throw new IllegalArgumentException("Unknown item: " + itemStringID);
		setMilliseconds(item, milliseconds);
	}

	public static long get(Item item) {
		if (item == null) return DEFAULT_TIME_MS;
		return itemTimes.getOrDefault(item, DEFAULT_TIME_MS);
	}

	public static long get(Recipe recipe) {
		if (recipe == null || recipe.resultItem == null) return DEFAULT_TIME_MS;
		return get(recipe.resultItem.item);
	}

	public static float getSeconds(Item item) {
		return get(item) / 1000.0F;
	}

	public static void reset(Item item) {
		if (item != null) itemTimes.remove(item);
	}
}
