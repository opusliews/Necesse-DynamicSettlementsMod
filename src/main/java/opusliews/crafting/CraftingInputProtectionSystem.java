package opusliews.crafting;

import necesse.engine.registries.ItemRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.recipe.Ingredient;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import opusliews.forge.ForgeCookingInput;
import opusliews.object.CraftingTaskBoardObjectEntity;
import opusliews.object.DynamicCraftingStationObjectEntity;

import java.awt.Point;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;

public final class CraftingInputProtectionSystem {
	private static final HashMap<String, WeakReference<DynamicCraftingStationObjectEntity>> inputOwners = new HashMap<>();
	private static final WeakHashMap<DynamicCraftingStationObjectEntity, HashSet<String>> stationKeys = new WeakHashMap<>();
	private static final WeakHashMap<DynamicCraftingStationObjectEntity, ProtectionCache> protectionCaches = new WeakHashMap<>();

	private CraftingInputProtectionSystem() {
	}

	public static synchronized void refreshStation(DynamicCraftingStationObjectEntity station) {
		if (station == null || !station.getLevel().isServer()) return;

		HashSet<String> oldKeys = stationKeys.remove(station);
		if (oldKeys != null) {
			for (String key : oldKeys) {
				WeakReference<DynamicCraftingStationObjectEntity> ownerRef = inputOwners.get(key);
				if (ownerRef != null && ownerRef.get() == station) inputOwners.remove(key);
			}
		}

		HashSet<String> newKeys = new HashSet<>();
		for (Point point : station.getInputStorages()) {
			String key = getStorageKey(station.getLevel().getIdentifier().toString(), point.x, point.y);
			inputOwners.put(key, new WeakReference<>(station));
			newKeys.add(key);
		}
		if (!newKeys.isEmpty()) stationKeys.put(station, newKeys);

		rebuildProtectionCache(station);
		cleanupOwners();
	}

	public static synchronized void refreshBoardConfiguration(CraftingTaskBoardObjectEntity board) {
		if (board == null || !board.getLevel().isServer()) return;
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null) return;
		Point boardPoint = station.getTaskBoard();
		if (boardPoint == null || boardPoint.x != board.tileX || boardPoint.y != board.tileY) return;
		rebuildProtectionCache(station);
	}

	public static synchronized void removeStation(DynamicCraftingStationObjectEntity station) {
		if (station == null) return;
		protectionCaches.remove(station);
		HashSet<String> keys = stationKeys.remove(station);
		if (keys == null) return;
		for (String key : keys) {
			WeakReference<DynamicCraftingStationObjectEntity> ownerRef = inputOwners.get(key);
			if (ownerRef != null && ownerRef.get() == station) inputOwners.remove(key);
		}
	}

	public static synchronized boolean isProtectedInputIngredient(SettlementInventory storage, InventoryItem item) {
		if (storage == null || storage.level == null || item == null || item.item == null) return false;
		String key = getStorageKey(storage.level.getIdentifier().toString(), storage.tileX, storage.tileY);
		WeakReference<DynamicCraftingStationObjectEntity> ownerRef = inputOwners.get(key);
		DynamicCraftingStationObjectEntity station = ownerRef == null ? null : ownerRef.get();
		if (station == null) {
			inputOwners.remove(key);
			return false;
		}

		ProtectionCache cache = protectionCaches.get(station);
		if (cache == null) {
			rebuildProtectionCache(station);
			cache = protectionCaches.get(station);
			if (cache == null) return false;
		}

		if (cache.exactItemIDs.contains(item.item.getID())) return true;
		for (Ingredient ingredient : cache.globalIngredients) {
			if (ingredient.matchesItem(item.item)) return true;
		}
		return false;
	}

	private static void rebuildProtectionCache(DynamicCraftingStationObjectEntity station) {
		ProtectionCache cache = new ProtectionCache();
		Point boardPoint = station.getTaskBoard();
		if (boardPoint == null) {
			protectionCaches.put(station, cache);
			return;
		}

		if (!(station.getLevel().entityManager.getObjectEntity(boardPoint.x, boardPoint.y) instanceof CraftingTaskBoardObjectEntity)) {
			protectionCaches.put(station, cache);
			return;
		}
		CraftingTaskBoardObjectEntity board = (CraftingTaskBoardObjectEntity)station.getLevel().entityManager.getObjectEntity(boardPoint.x, boardPoint.y);
		if (board.getLinkedStationEntity() != station) {
			protectionCaches.put(station, cache);
			return;
		}

		LinkedHashMap<String, Ingredient> globalIngredients = new LinkedHashMap<>();
		for (CraftingTask task : board.getTasks()) {
			if (task == null) continue;
			CraftingTaskRecipe taskRecipe = CraftingTaskLogic.getTaskRecipe(board, task);
			if (taskRecipe == null) continue;
			if (taskRecipe.type == CraftingTaskRecipe.Type.CUSTOM_FORGE) {
				addForgeInput(cache, taskRecipe.forgeRecipe.firstInput);
				addForgeInput(cache, taskRecipe.forgeRecipe.secondInput);
			} else if (taskRecipe.recipe != null) {
				for (Ingredient ingredient : taskRecipe.recipe.ingredients) {
					if (ingredient == null) continue;
					if (ingredient.isGlobalIngredient()) {
						globalIngredients.putIfAbsent(ingredient.ingredientStringID, ingredient);
					} else {
						int itemID = ItemRegistry.getItemID(ingredient.ingredientStringID);
						if (itemID >= 0) cache.exactItemIDs.add(itemID);
					}
				}
			}
		}
		cache.globalIngredients.addAll(globalIngredients.values());
		protectionCaches.put(station, cache);
	}

	private static void addForgeInput(ProtectionCache cache, ForgeCookingInput input) {
		if (input == null) return;
		int itemID = ItemRegistry.getItemID(input.itemStringID);
		if (itemID >= 0) cache.exactItemIDs.add(itemID);
	}

	private static String getStorageKey(String levelIdentifier, int tileX, int tileY) {
		return levelIdentifier + ":" + tileX + ":" + tileY;
	}

	private static void cleanupOwners() {
		inputOwners.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().get() == null);
	}

	private static final class ProtectionCache {
		private final HashSet<Integer> exactItemIDs = new HashSet<>();
		private final ArrayList<Ingredient> globalIngredients = new ArrayList<>();
	}
}
