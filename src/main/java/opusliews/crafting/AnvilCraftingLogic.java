package opusliews.crafting;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryItemsRemoved;
import necesse.inventory.InventoryRange;
import necesse.inventory.item.Item;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.RecipeCraftedEvent;
import necesse.inventory.recipe.Recipes;
import necesse.inventory.recipe.Tech;
import necesse.level.gameObject.container.CraftingStationObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import opusliews.object.AnvilCraftingTaskBoardObjectEntity;
import opusliews.object.AnvilObjectEntity;

public final class AnvilCraftingLogic {
	private AnvilCraftingLogic() {
	}

	public static Recipe getRecipe(AnvilCraftingTaskBoardObjectEntity board, int itemID) {
		LevelObject anvil = board.getValidLinkedAnvilObject();
		if (anvil == null || !(anvil.object instanceof CraftingStationObject)) {
			return null;
		}

		Tech[] techs = ((CraftingStationObject)anvil.object).getCraftingTechs();
		return Recipes.streamRecipes()
				.filter(recipe -> recipe.resultItem.item.getID() == itemID)
				.filter(recipe -> {
					for (Tech tech : techs) {
						if (recipe.matchTech(tech)) {
							return true;
						}
					}
					return false;
				})
				.findFirst()
				.orElse(null);
	}

	public static AnvilObjectEntity getAnvilEntity(AnvilCraftingTaskBoardObjectEntity board) {
		Point point = board.getLinkedAnvil();
		if (point == null) {
			return null;
		}

		ObjectEntity entity = board.getLevel().entityManager.getObjectEntity(point.x, point.y);
		return entity instanceof AnvilObjectEntity ? (AnvilObjectEntity)entity : null;
	}

	public static InventoryRange getInputRange(AnvilCraftingTaskBoardObjectEntity board) {
		AnvilObjectEntity anvil = getAnvilEntity(board);
		return anvil == null ? null : getLinkedStorageRange(board.getLevel(), anvil.getInputStorage());
	}

	public static InventoryRange getOutputRange(AnvilCraftingTaskBoardObjectEntity board) {
		AnvilObjectEntity anvil = getAnvilEntity(board);
		return anvil == null ? null : getLinkedStorageRange(board.getLevel(), anvil.getOutputStorage());
	}

	private static InventoryRange getLinkedStorageRange(Level level, Point point) {
		if (point == null) {
			return null;
		}

		LevelObject object = level.getLevelObject(point.x, point.y);
		LevelObject master = object == null ? null : (LevelObject)object.getMasterLevelObject().orElse(null);
		if (master == null) {
			return null;
		}

		ObjectEntity entity = master.getObjectEntity();
		if (!(entity instanceof OEInventory)) {
			return null;
		}

		OEInventory inventory = (OEInventory)entity;
		InventoryRange range = inventory.getSettlementStorage();
		if (range != null) {
			return range;
		}

		Inventory raw = inventory.getInventory();
		return raw == null ? null : new InventoryRange(raw);
	}

	public static List<String> getMissingIngredients(Level level, InventoryRange input, Recipe recipe) {
		ArrayList<String> missing = new ArrayList<>();
		if (input == null) {
			missing.add("Input storage is not linked");
			return missing;
		}

		Inventory simulated = input.inventory.copy();
		Set<String> seen = new HashSet<>();
		for (Ingredient ingredient : recipe.ingredients) {
			int needed = ingredient.getIngredientAmount();
			if (needed <= 0) {
				InventoryRange simulatedRange = new InventoryRange(simulated, input.startSlot, input.endSlot);
				if (!ingredient.hasIngredientRange(level, null, simulatedRange) && seen.add(ingredient.getDisplayName())) {
					missing.add(ingredient.getDisplayName());
				}
				continue;
			}

			int removed = simulated.removeItems(
					level,
					null,
					ingredient,
					null,
					needed,
					input.startSlot,
					input.endSlot,
					null
			);
			if (removed < needed && seen.add(ingredient.getDisplayName())) {
				missing.add(ingredient.getDisplayName());
			}
		}
		return missing;
	}

	public static boolean canFitResult(Level level, InventoryRange output, InventoryItem result) {
		return output != null && output.inventory.canAddItem(
				level,
				null,
				result,
				output.startSlot,
				output.endSlot,
				"anvilcrafting"
		) >= result.getAmount();
	}

	public static int countSettlementStock(
			AnvilCraftingTaskBoardObjectEntity board,
			ServerSettlementData settlement,
			int itemID
	) {
		if (settlement == null) {
			return 0;
		}

		Level level = board.getLevel();
		Item item = necesse.engine.registries.ItemRegistry.getItem(itemID);
		if (item == null) {
			return 0;
		}

		int count = 0;
		Point outputPoint = null;
		AnvilObjectEntity anvil = getAnvilEntity(board);
		if (anvil != null) {
			outputPoint = anvil.getOutputStorage();
		}

		boolean outputCounted = false;
		for (Object object : settlement.storageManager.getStorage()) {
			if (!(object instanceof SettlementInventory)) {
				continue;
			}

			SettlementInventory settlementInventory = (SettlementInventory)object;
			InventoryRange range = settlementInventory.getInventoryRange();
			if (range == null) {
				continue;
			}

			count += range.inventory.getAmount(
					level,
					null,
					item,
					range.startSlot,
					range.endSlot,
					"anvilstock"
			);

			if (outputPoint != null && settlementInventory.tileX == outputPoint.x && settlementInventory.tileY == outputPoint.y) {
				outputCounted = true;
			}
		}

		if (!outputCounted) {
			InventoryRange output = getOutputRange(board);
			if (output != null) {
				count += output.inventory.getAmount(
						level,
						null,
						item,
						output.startSlot,
						output.endSlot,
						"anvilstock"
				);
			}
		}

		return count;
	}

	public static CraftResult craftOne(AnvilCraftingTaskBoardObjectEntity board, Recipe recipe) {
		Level level = board.getLevel();
		InventoryRange input = getInputRange(board);
		InventoryRange output = getOutputRange(board);
		if (input == null) {
			return CraftResult.problem("Input storage is not linked");
		}
		if (output == null) {
			return CraftResult.problem("Output storage is not linked");
		}

		List<String> missing = getMissingIngredients(level, input, recipe);
		if (!missing.isEmpty()) {
			return CraftResult.missing(missing);
		}

		InventoryItem expectedResult = recipe.resultItem.copy(recipe.resultAmount);
		if (!canFitResult(level, output, expectedResult)) {
			return CraftResult.problem("Output storage is full");
		}

		ArrayList<InventoryItemsRemoved> usedItems = new ArrayList<>();
		for (Ingredient ingredient : recipe.ingredients) {
			int needed = ingredient.getIngredientAmount();
			if (needed <= 0) {
				continue;
			}

			int removed = input.inventory.removeItems(
					level,
					null,
					ingredient,
					null,
					needed,
					input.startSlot,
					input.endSlot,
					usedItems
			);
			if (removed < needed) {
				for (InventoryItemsRemoved used : usedItems) {
					used.revert();
				}
				return CraftResult.missing(getMissingIngredients(level, input, recipe));
			}
		}

		RecipeCraftedEvent event = new RecipeCraftedEvent(recipe, new ArrayList<>(usedItems));
		recipe.submitCraftedEvent(event);
		InventoryItem result = event.resultItem;
		if (!canFitResult(level, output, result)) {
			for (InventoryItemsRemoved used : usedItems) {
				used.revert();
			}
			return CraftResult.problem("Output storage is full");
		}

		InventoryItem remaining = result.copy();
		output.inventory.addItem(
				level,
				null,
				remaining,
				output.startSlot,
				output.endSlot,
				"anvilcrafting"
		);
		if (remaining.getAmount() > 0) {
			for (InventoryItemsRemoved used : usedItems) {
				used.revert();
			}
			return CraftResult.problem("Output storage is full");
		}

		return CraftResult.success(result.getAmount());
	}

	public static class CraftResult {
		public final boolean success;
		public final int producedAmount;
		public final ArrayList<String> problems;

		private CraftResult(boolean success, int producedAmount, List<String> problems) {
			this.success = success;
			this.producedAmount = producedAmount;
			this.problems = new ArrayList<>(problems);
		}

		public static CraftResult success(int producedAmount) {
			return new CraftResult(true, producedAmount, new ArrayList<>());
		}

		public static CraftResult problem(String problem) {
			ArrayList<String> problems = new ArrayList<>();
			problems.add(problem);
			return new CraftResult(false, 0, problems);
		}

		public static CraftResult missing(List<String> missing) {
			ArrayList<String> problems = new ArrayList<>();
			if (missing.isEmpty()) {
				problems.add("Missing ingredients");
			} else {
				problems.add("Missing ingredients: " + String.join(", ", missing));
			}
			return new CraftResult(false, 0, problems);
		}
	}
}
