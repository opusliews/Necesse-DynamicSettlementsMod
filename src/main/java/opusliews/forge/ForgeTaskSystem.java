package opusliews.forge;

import necesse.engine.localization.Localization;
import necesse.engine.registries.ItemRegistry;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import opusliews.crafting.CraftingStoragePool;
import opusliews.crafting.CraftingTaskRecipe;
import opusliews.logging.Logging;
import opusliews.object.CraftingTaskBoardObjectEntity;
import opusliews.object.DynamicCraftingStationObjectEntity;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ForgeTaskSystem {
	private static final String CHARCOAL_ID = "charcoal";

	private ForgeTaskSystem() {
	}

	public static List<String> getMissingIngredients(CraftingTaskBoardObjectEntity board, CraftingTaskRecipe taskRecipe) {
		if (taskRecipe == null || !taskRecipe.isForgeRecipe()) return new ArrayList<>();
		CraftingStoragePool pool = CraftingTaskLogicBridge.getStoragePool(board);
		if (!pool.hasInputs()) {
			ArrayList<String> missing = new ArrayList<>();
			missing.add(Localization.translate("ui", "craftinginputnotlinked"));
			return missing;
		}

		if (taskRecipe.type == CraftingTaskRecipe.Type.VANILLA_FORGE) {
			return pool.getMissingIngredients(taskRecipe.recipe);
		}

		ArrayList<String> missing = new ArrayList<>();
		ForgeCookingRecipe recipe = taskRecipe.forgeRecipe;
		if (recipe == null) return missing;
		appendMissingExact(pool, recipe.firstInput, missing);
		appendMissingExact(pool, recipe.secondInput, missing);
		return missing;
	}

	private static void appendMissingExact(CraftingStoragePool pool, ForgeCookingInput input, ArrayList<String> missing) {
		if (input == null || pool.getExactAmount(input.itemStringID) >= input.amount) return;
		Item item = ItemRegistry.getItem(input.itemStringID);
		String name = item == null ? input.itemStringID : new InventoryItem(item).getItemDisplayName();
		if (!missing.contains(name)) missing.add(name);
	}

	public static boolean hasAvailableForge(CraftingTaskBoardObjectEntity board, CraftingTaskRecipe taskRecipe) {
		if (board == null || taskRecipe == null || !taskRecipe.isForgeRecipe()) return false;
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null || !station.supportsForgeLinks()) return false;
		InventoryItem result = taskRecipe.getResultItem();
		if (result == null) return false;
		for (ProcessingForgeObjectEntity forge : station.getValidLinkedForges()) {
			if (isAvailableForNewTask(forge, result)) return true;
		}
		return false;
	}


	public static boolean hasFuelAvailable(CraftingTaskBoardObjectEntity board, CraftingTaskRecipe taskRecipe) {
		if (board == null || taskRecipe == null || !taskRecipe.isForgeRecipe()) return false;
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null || !station.supportsForgeLinks()) return false;

		InventoryItem result = taskRecipe.getResultItem();
		if (result == null) return false;

		CraftingStoragePool pool = CraftingTaskLogicBridge.getStoragePool(board);
		boolean externalFuelAvailable = pool.getExactAmount(CHARCOAL_ID) > 0;
		Item charcoalItem = ItemRegistry.getItem(CHARCOAL_ID);

		for (ProcessingForgeObjectEntity forge : station.getValidLinkedForges()) {
			if (!isAvailableForNewTask(forge, result)) continue;
			if (ForgeHeatSystem.isRunning(forge) || ForgeHeatSystem.hasStoredFuel(forge)) return true;
			if (!externalFuelAvailable || charcoalItem == null) continue;

			InventoryItem oneCharcoal = new InventoryItem(charcoalItem, 1);
			if (forge.inventory.canAddItem(forge.getLevel(), null, oneCharcoal, 0, forge.fuelSlots - 1, "npcforgefuelcheck") > 0) {
				return true;
			}
		}
		return false;
	}

	public static QueueResult queueOne(CraftingTaskBoardObjectEntity board, CraftingTaskRecipe taskRecipe) {
		if (board == null || taskRecipe == null || !taskRecipe.isForgeRecipe()) {
			return QueueResult.problem(Localization.translate("ui", "craftingrecipeunavailable"));
		}

		CraftingStoragePool pool = CraftingTaskLogicBridge.getStoragePool(board);
		if (!pool.hasInputs()) return QueueResult.problem(Localization.translate("ui", "craftinginputnotlinked"));
		if (!pool.hasOutputs()) return QueueResult.problem(Localization.translate("ui", "craftingoutputnotlinked"));

		List<String> missing = getMissingIngredients(board, taskRecipe);
		if (!missing.isEmpty()) {
			return QueueResult.problem(Localization.translate("ui", "craftingmissingingredientslist", "ingredients", String.join(", ", missing)));
		}

		InventoryItem result = taskRecipe.getResultItem();
		if (result == null || !pool.canFitResult(result)) {
			return QueueResult.problem(Localization.translate("ui", "craftingoutputfull"));
		}

		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null || !station.supportsForgeLinks()) {
			return QueueResult.problem(Localization.translate("ui", "craftingrequireslinkedforge"));
		}
		if (!hasFuelAvailable(board, taskRecipe)) {
			Logging.logMessage("[CraftingForgeJob] Blocked output=" + result.item.getStringID() + " reason=missing_forge_fuel");
			return QueueResult.problem(Localization.translate("ui", "statusmissingforgefuel"));
		}

		ProcessingForgeObjectEntity forge = selectForge(station.getValidLinkedForges(), result);
		if (forge == null) return QueueResult.problem(Localization.translate("ui", "craftingrecipeunavailable"));

		boolean loaded = taskRecipe.type == CraftingTaskRecipe.Type.CUSTOM_FORGE
				? loadCustomRecipe(pool, forge, taskRecipe.forgeRecipe)
				: loadVanillaRecipe(pool, forge, taskRecipe.recipe);
		if (!loaded) return QueueResult.problem(Localization.translate("ui", "craftingmissingingredients"));

		if (!ensureForgeFuel(pool, forge, taskRecipe.getProcessTime(forge.getLevel()))) {
			returnInputsToPool(pool, forge);
			return QueueResult.problem(Localization.translate("ui", "craftingrequiresforgefuel"));
		}

		forge.forceNextUpdate();
		forge.inventory.markFullDirty();
		board.addForgeAssignment(new Point(forge.tileX, forge.tileY), result.item.getID(), result.getAmount());
		Logging.logMessage("[CraftingForgeJob] Queued output=" + result.item.getStringID()
				+ "x" + result.getAmount() + " forge=" + forge.tileX + "," + forge.tileY);
		return QueueResult.success();
	}

	private static ProcessingForgeObjectEntity selectForge(List<ProcessingForgeObjectEntity> forges, InventoryItem result) {
		ArrayList<ProcessingForgeObjectEntity> candidates = new ArrayList<>();
		for (ProcessingForgeObjectEntity forge : forges) {
			if (isAvailableForNewTask(forge, result)) candidates.add(forge);
		}
		candidates.sort(Comparator
				.comparing((ProcessingForgeObjectEntity forge) -> !ForgeHeatSystem.isRunning(forge))
				.thenComparingInt(forge -> -ForgeHeatSystem.getRemainingFuelTime(forge)));
		return candidates.isEmpty() ? null : candidates.get(0);
	}

	private static boolean isAvailableForNewTask(ProcessingForgeObjectEntity forge, InventoryItem result) {
		if (forge == null || result == null) return false;
		if (forge.getNextProcessTask() != null) return false;
		for (int slot = forge.fuelSlots; slot < forge.fuelSlots + forge.inputSlots; slot++) {
			if (!forge.inventory.isSlotClear(slot)) return false;
		}
		for (int slot = forge.fuelSlots + forge.inputSlots; slot < forge.inventory.getSize(); slot++) {
			if (!forge.inventory.isSlotClear(slot)) return false;
		}
		return forge.canAddOutput(result.copy());
	}

	private static boolean loadCustomRecipe(CraftingStoragePool pool, ProcessingForgeObjectEntity forge, ForgeCookingRecipe recipe) {
		if (recipe == null) return false;

		InventoryItem first = null;
		if (recipe.firstInput != null) {
			first = pool.takeExactItem(
					recipe.firstInput.itemStringID,
					recipe.firstInput.amount,
					recipe.firstInput.resultBehavior == ForgeCookingInput.ResultBehavior.DURABILITY_USE
			);
			if (first == null) return false;
		}

		InventoryItem second = null;
		if (recipe.secondInput != null) {
			second = pool.takeExactItem(
					recipe.secondInput.itemStringID,
					recipe.secondInput.amount,
					recipe.secondInput.resultBehavior == ForgeCookingInput.ResultBehavior.DURABILITY_USE
			);
			if (second == null) {
				if (first != null) pool.addInputItemOrdered(first);
				return false;
			}
		}

		int slot = forge.fuelSlots;
		if (first != null) {
			forge.inventory.setItem(slot, first);
			forge.inventory.markDirty(slot);
			slot++;
		}
		if (second != null) {
			forge.inventory.setItem(slot, second);
			forge.inventory.markDirty(slot);
		}
		return true;
	}

	private static boolean loadVanillaRecipe(CraftingStoragePool pool, ProcessingForgeObjectEntity forge, Recipe recipe) {
		if (recipe == null || recipe.ingredients.length == 0 || recipe.ingredients.length > forge.inputSlots) return false;
		ArrayList<InventoryItem> taken = new ArrayList<>();
		for (Ingredient ingredient : recipe.ingredients) {
			InventoryItem item = pool.takeIngredient(ingredient);
			if (item == null) {
				for (InventoryItem restore : taken) pool.addInputItemOrdered(restore);
				return false;
			}
			taken.add(item);
		}
		for (int i = 0; i < taken.size(); i++) {
			int slot = forge.fuelSlots + i;
			forge.inventory.setItem(slot, taken.get(i));
			forge.inventory.markDirty(slot);
		}
		return true;
	}

	private static boolean ensureForgeFuel(CraftingStoragePool pool, ProcessingForgeObjectEntity forge, int processTime) {
		int remainingHeat = ForgeHeatSystem.getRemainingFuelTime(forge);
		if (remainingHeat >= processTime || ForgeHeatSystem.hasStoredFuel(forge)) return true;

		InventoryItem fuel = pool.takeExactItem(CHARCOAL_ID, 1, false);
		if (fuel == null) return ForgeHeatSystem.isRunning(forge);
		if (!forge.inventory.addItem(forge.getLevel(), null, fuel, 0, forge.fuelSlots - 1, "npcforgefuel")) {
			pool.addInputItemOrdered(fuel);
			return false;
		}
		return true;
	}

	public static boolean collectAssignment(CraftingTaskBoardObjectEntity board, Point forgePoint, int outputItemID, int outputAmount) {
		if (board == null || forgePoint == null) return false;
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null) return false;
		ProcessingForgeObjectEntity forge = station.getLinkedForge(forgePoint);
		if (forge == null) return false;

		CraftingStoragePool pool = CraftingTaskLogicBridge.getStoragePool(board);
		Item outputItem = ItemRegistry.getItem(outputItemID);
		if (outputItem == null) return false;

		int remaining = Math.max(1, outputAmount);
		for (int slot = forge.fuelSlots + forge.inputSlots; slot < forge.inventory.getSize() && remaining > 0; slot++) {
			InventoryItem item = forge.inventory.getItem(slot);
			if (item == null || item.item.getID() != outputItemID) continue;
			int move = Math.min(remaining, item.getAmount());
			InventoryItem moving = item.copy(move);
			if (!pool.addResultOrdered(moving)) return false;
			item.setAmount(item.getAmount() - move);
			if (item.getAmount() <= 0) forge.inventory.clearSlot(slot); else forge.inventory.markDirty(slot);
			remaining -= move;
		}
		if (remaining > 0) return false;

		returnInputsToPool(pool, forge);
		forge.inventory.markFullDirty();
		forge.forceNextUpdate();
		return true;
	}

	public static boolean isAssignmentStillProcessing(CraftingTaskBoardObjectEntity board, Point forgePoint) {
		DynamicCraftingStationObjectEntity station = board == null ? null : board.getLinkedStationEntity();
		ProcessingForgeObjectEntity forge = station == null ? null : station.getLinkedForge(forgePoint);
		if (forge == null) return false;
		if (forge.getNextProcessTask() != null) return true;
		for (int slot = forge.fuelSlots; slot < forge.fuelSlots + forge.inputSlots; slot++) {
			if (!forge.inventory.isSlotClear(slot)) return true;
		}
		return false;
	}

	private static void returnInputsToPool(CraftingStoragePool pool, ProcessingForgeObjectEntity forge) {
		for (int slot = forge.fuelSlots; slot < forge.fuelSlots + forge.inputSlots; slot++) {
			InventoryItem item = forge.inventory.getItem(slot);
			if (item == null) continue;
			InventoryItem moving = item.copy();
			if (pool.addInputItemOrdered(moving)) forge.inventory.clearSlot(slot);
		}
	}

	public static final class QueueResult {
		public final boolean success;
		public final String problem;

		private QueueResult(boolean success, String problem) {
			this.success = success;
			this.problem = problem;
		}

		public static QueueResult success() {
			return new QueueResult(true, null);
		}

		public static QueueResult problem(String problem) {
			return new QueueResult(false, problem);
		}
	}

	private static final class CraftingTaskLogicBridge {
		private static CraftingStoragePool getStoragePool(CraftingTaskBoardObjectEntity board) {
			return opusliews.crafting.CraftingTaskLogic.getStoragePool(board);
		}
	}
}
