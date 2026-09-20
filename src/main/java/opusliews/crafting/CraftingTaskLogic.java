package opusliews.crafting;

import necesse.engine.localization.Localization;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryItemsRemoved;
import necesse.inventory.InventoryRange;
import necesse.inventory.item.Item;
import necesse.inventory.recipe.Recipe;
import necesse.inventory.recipe.RecipeCraftedEvent;
import necesse.inventory.recipe.Recipes;
import necesse.inventory.recipe.Tech;
import necesse.level.gameObject.container.CraftingStationObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import opusliews.object.CraftingTaskBoardObjectEntity;
import opusliews.object.DynamicCraftingStationObjectEntity;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CraftingTaskLogic {
	private CraftingTaskLogic() {
	}

	public static Recipe getRecipe(CraftingTaskBoardObjectEntity board, int itemID) {
		LevelObject stationObject = board.getValidLinkedStationObject();
		if (stationObject == null || !(stationObject.object instanceof CraftingStationObject)) return null;

		Tech[] techs = ((CraftingStationObject)stationObject.object).getCraftingTechs();
		return Recipes.streamRecipes()
				.filter(recipe -> recipe.resultItem.item.getID() == itemID)
				.filter(recipe -> {
					for (Tech tech : techs) {
						if (recipe.matchTech(tech)) return true;
					}
					return false;
				})
				.findFirst()
				.orElse(null);
	}

	public static DynamicCraftingStationObjectEntity getStationEntity(CraftingTaskBoardObjectEntity board) {
		return board.getLinkedStationEntity();
	}

	public static String getStationCraftingProblem(CraftingTaskBoardObjectEntity board, Recipe recipe) {
		DynamicCraftingStationObjectEntity station = getStationEntity(board);
		return station == null ? Localization.translate("ui", "craftingboardinvalidstation") : station.getSettlerCraftingProblem(recipe);
	}

	public static CraftingStoragePool getStoragePool(CraftingTaskBoardObjectEntity board) {
		DynamicCraftingStationObjectEntity station = getStationEntity(board);
		if (station == null) return CraftingStoragePool.from(board.getLevel(), new ArrayList<>(), new ArrayList<>());
		return CraftingStoragePool.from(board.getLevel(), station.getInputStorages(), station.getOutputStorages());
	}

	public static List<String> getMissingIngredients(CraftingTaskBoardObjectEntity board, Recipe recipe) {
		return getStoragePool(board).getMissingIngredients(recipe);
	}

	public static boolean canFitResult(CraftingTaskBoardObjectEntity board, Recipe recipe, InventoryItem result) {
		return getStoragePool(board).canFitResultAfterIngredients(recipe, result);
	}

	public static boolean hasOutputStorage(CraftingTaskBoardObjectEntity board) {
		return getStoragePool(board).hasOutputs();
	}

	public static int countSettlementStock(
			CraftingTaskBoardObjectEntity board,
			ServerSettlementData settlement,
			int itemID
	) {
		if (settlement == null) return 0;

		Level level = board.getLevel();
		Item item = necesse.engine.registries.ItemRegistry.getItem(itemID);
		if (item == null) return 0;

		int count = 0;
		DynamicCraftingStationObjectEntity station = getStationEntity(board);
		List<Point> outputPoints = station == null ? new ArrayList<>() : station.getOutputStorages();
		Set<Point> settlementStoragePoints = new HashSet<>();

		for (Object object : settlement.storageManager.getStorage()) {
			if (!(object instanceof SettlementInventory)) continue;

			SettlementInventory settlementInventory = (SettlementInventory)object;
			InventoryRange range = settlementInventory.getInventoryRange();
			if (range == null) continue;

			count += range.inventory.getAmount(
					level,
					null,
					item,
					range.startSlot,
					range.endSlot,
					"craftingstock"
			);
			settlementStoragePoints.add(new Point(settlementInventory.tileX, settlementInventory.tileY));
		}

		for (Point outputPoint : outputPoints) {
			if (settlementStoragePoints.contains(outputPoint)) continue;
			InventoryRange output = CraftingStoragePool.getLinkedStorageRange(level, outputPoint);
			if (output == null) continue;

			count += output.inventory.getAmount(
					level,
					null,
					item,
					output.startSlot,
					output.endSlot,
					"craftingstock"
			);
			settlementStoragePoints.add(new Point(outputPoint));
		}

		return count;
	}

	public static CraftResult craftOne(CraftingTaskBoardObjectEntity board, Recipe recipe) {
		CraftingStoragePool pool = getStoragePool(board);
		if (!pool.hasInputs()) return CraftResult.problem(Localization.translate("ui", "craftinginputnotlinked"));
		if (!pool.hasOutputs()) return CraftResult.problem(Localization.translate("ui", "craftingoutputnotlinked"));

		String stationProblem = getStationCraftingProblem(board, recipe);
		if (stationProblem != null) return CraftResult.problem(stationProblem);

		List<String> missing = pool.getMissingIngredients(recipe);
		if (!missing.isEmpty()) return CraftResult.missing(missing);

		InventoryItem expectedResult = recipe.resultItem.copy(recipe.resultAmount);
		if (!pool.canFitResultAfterIngredients(recipe, expectedResult)) {
			return CraftResult.problem(Localization.translate("ui", "craftingoutputfull"));
		}

		ArrayList<InventoryItemsRemoved> usedItems = new ArrayList<>();
		if (!pool.removeIngredients(recipe, usedItems)) {
			revertUsedItems(usedItems);
			return CraftResult.missing(pool.getMissingIngredients(recipe));
		}

		RecipeCraftedEvent event = new RecipeCraftedEvent(recipe, new ArrayList<>(usedItems));
		recipe.submitCraftedEvent(event);
		InventoryItem result = event.resultItem;
		if (!pool.canFitResult(result)) {
			revertUsedItems(usedItems);
			return CraftResult.problem(Localization.translate("ui", "craftingoutputfull"));
		}

		if (!pool.addResultOrdered(result)) {
			revertUsedItems(usedItems);
			return CraftResult.problem(Localization.translate("ui", "craftingoutputfull"));
		}

		return CraftResult.success(result);
	}

	private static void revertUsedItems(ArrayList<InventoryItemsRemoved> usedItems) {
		for (InventoryItemsRemoved used : usedItems) used.revert();
	}

	public static class CraftResult {
		public final boolean success;
		public final int producedAmount;
		public final InventoryItem resultItem;
		public final ArrayList<String> problems;

		private CraftResult(boolean success, InventoryItem resultItem, List<String> problems) {
			this.success = success;
			this.resultItem = resultItem == null ? null : resultItem.copy();
			this.producedAmount = resultItem == null ? 0 : resultItem.getAmount();
			this.problems = new ArrayList<>(problems);
		}

		public static CraftResult success(InventoryItem resultItem) {
			return new CraftResult(true, resultItem, new ArrayList<>());
		}

		public static CraftResult problem(String problem) {
			ArrayList<String> problems = new ArrayList<>();
			problems.add(problem);
			return new CraftResult(false, null, problems);
		}

		public static CraftResult missing(List<String> missing) {
			ArrayList<String> problems = new ArrayList<>();
			if (missing.isEmpty()) {
				problems.add(Localization.translate("ui", "craftingmissingingredients"));
			} else {
				problems.add(Localization.translate("ui", "craftingmissingingredientslist", "ingredients", String.join(", ", missing)));
			}
			return new CraftResult(false, null, problems);
		}
	}
}
