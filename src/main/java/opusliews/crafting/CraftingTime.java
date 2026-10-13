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

	// Player-only crafting speed. NPC timing intentionally continues using get(recipe).
	public static long getManual(Recipe recipe, String stationID) {
		long base = get(recipe);
		int tier = getManualStationTier(stationID);
		return Math.max(1L, base / (1L << tier));
	}

	private static int getManualStationTier(String stationID) {
		if (stationID == null) return 0;
		switch (stationID) {
			case "demonicworkstationduo":
			case "demonicanvil":
			case "voidalchemytable":
				return 1;
			case "tungstenworkstation":
			case "tungstenanvil":
			case "caveglowalchemytable":
			case "tungstencarpentersbench":
				return 2;
			case "fallenworkstation":
			case "fallenanvil":
			case "fallenalchemytable":
			case "fallencarpentersbench":
				return 3;
			default:
				return 0;
		}
	}

	public static float getSeconds(Item item) {
		return get(item) / 1000.0F;
	}

	public static void reset(Item item) {
		if (item != null) itemTimes.remove(item);
	}
}
