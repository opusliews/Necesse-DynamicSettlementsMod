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
import opusliews.crafting.CraftingTask;
import opusliews.crafting.CraftingTaskLogic;
import opusliews.logging.Logging;
import opusliews.durability.ItemDurabilitySystem;
import opusliews.item.MoldItem;
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
			if (isAvailableForNewTask(forge, result) || getSharedInputSlot(board, taskRecipe, forge) >= 0) return true;
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
			if (!isAvailableForNewTask(forge, result) && getSharedInputSlot(board, taskRecipe, forge) < 0) continue;
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
		return queueOne(board, taskRecipe, null);
	}

	public static QueueResult queueOne(CraftingTaskBoardObjectEntity board, CraftingTaskRecipe taskRecipe, CraftingTask task) {
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
		int inputSlot = 0;
		if (forge == null) {
			for (ProcessingForgeObjectEntity candidate : station.getValidLinkedForges()) {
				int sharedSlot = getSharedInputSlot(board, taskRecipe, candidate);
				if (sharedSlot < 0) continue;
				forge = candidate;
				inputSlot = sharedSlot;
				break;
			}
		}
		if (forge == null) return QueueResult.problem(Localization.translate("ui", "craftingrecipeunavailable"));

		boolean loaded = inputSlot != 0
				? loadSharedSingleInput(pool, forge, taskRecipe, inputSlot)
				: (taskRecipe.type == CraftingTaskRecipe.Type.CUSTOM_FORGE
						? loadCustomRecipe(pool, forge, taskRecipe.forgeRecipe)
						: loadVanillaRecipe(pool, forge, taskRecipe.recipe));
		if (!loaded) return QueueResult.problem(Localization.translate("ui", "craftingmissingingredients"));

		// Only stack independent, consumable single-input operations. Durable molds,
		// multi-input forging and special inventory behaviors retain the legacy path.
		int crafts = taskRecipe.type == CraftingTaskRecipe.Type.CUSTOM_FORGE && inputSlot == 0
				? preloadMoldBatch(board, task, taskRecipe.forgeRecipe, pool, forge, result)
				: preloadSingleInputBatch(board, task, taskRecipe, pool, forge, result, inputSlot);
		int outputAmount = result.getAmount() * crafts;
		if (Logging.logEnabled) Logging.logMessage("[CraftingForgeJob] Batch preload crafts=" + crafts
				+ " output=" + result.item.getStringID() + " requested=" + (task == null ? "unknown" : task.amount));

		if (!ensureForgeFuel(pool, forge, taskRecipe.getProcessTime(forge.getLevel()))) {
			returnInputSlotToPool(pool, forge, inputSlot);
			if (taskRecipe.type == CraftingTaskRecipe.Type.CUSTOM_FORGE && taskRecipe.forgeRecipe != null
					&& taskRecipe.forgeRecipe.secondInput != null && inputSlot == 0) {
				returnInputSlotToPool(pool, forge, 1);
			}
			return QueueResult.problem(Localization.translate("ui", "craftingrequiresforgefuel"));
		}

		forge.forceNextUpdate();
		forge.inventory.markFullDirty();
		board.addForgeAssignment(new Point(forge.tileX, forge.tileY), result.item.getID(), outputAmount, inputSlot);
		Logging.logMessage("[CraftingForgeJob] Queued output=" + result.item.getStringID()
				+ "x" + outputAmount + " forge=" + forge.tileX + "," + forge.tileY);
		return QueueResult.success();
	}


	/** Preload ore against a single mold's remaining guaranteed crafting uses. */
	private static int preloadMoldBatch(CraftingTaskBoardObjectEntity board, CraftingTask task,
			ForgeCookingRecipe recipe, CraftingStoragePool pool, ProcessingForgeObjectEntity forge, InventoryItem output) {
		if (task == null || task.conditionType != CraftingTask.CONDITION_CRAFT_UNITS
				|| recipe == null || recipe.firstInput == null || recipe.secondInput == null
				|| recipe.firstInput.resultBehavior != ForgeCookingInput.ResultBehavior.CONSUME
				|| recipe.secondInput.resultBehavior != ForgeCookingInput.ResultBehavior.DURABILITY_USE
				|| recipe.secondInput.amount != 1 || output == null || output.getAmount() <= 0) return 1;
		// Currently all registered molds use exactly one durability per craft.
		// Only batch known MoldItem instances; other durability-use ingredients keep legacy behavior.
		InventoryItem mold = forge.inventory.getItem(forge.fuelSlots + 1);
		InventoryItem material = forge.inventory.getItem(forge.fuelSlots);
		if (mold == null || !(mold.item instanceof MoldItem) || material == null
				|| !recipe.firstInput.itemStringID.equals(material.item.getStringID())) return 1;
		int durabilityUses = MoldItem.hasUnbreaking(mold) ? 999 : ItemDurabilitySystem.getDurability(mold);
		if (durabilityUses <= 1) return 1;
		int outstanding = Math.max(0, task.amount - board.getPendingForgeAmount(task.itemID));
		int demandCrafts = (int)Math.min(999L,
				((long)outstanding + output.getAmount() - 1) / output.getAmount());
		int wanted = Math.min(durabilityUses, demandCrafts);
		int activeTasks = 0;
		for (CraftingTask queued : board.getTasks()) {
			if (queued.sourceType == CraftingTask.SOURCE_FORGE && !queued.paused && queued.amount > 0) activeTasks++;
		}
		if (activeTasks > 1) wanted = Math.min(wanted, 10);
		int perCraft = recipe.firstInput.amount;
		if (perCraft <= 0) return 1;
		int crafts = 1;
		for (; crafts < wanted; crafts++) {
			if (pool.getExactAmount(recipe.firstInput.itemStringID) < perCraft) break;
			InventoryItem extra = new InventoryItem(material.item, perCraft);
			int slot = forge.fuelSlots;
			if (forge.inventory.canAddItem(forge.getLevel(), null, extra, slot, slot, "npcforgemoldbatch") < perCraft) break;
			InventoryItem taken = pool.takeExactItem(recipe.firstInput.itemStringID, perCraft, false);
			if (taken == null) break;
			if (!forge.inventory.addItem(forge.getLevel(), null, taken, slot, slot, "npcforgemoldbatch")) {
				if (!pool.addInputItemOrdered(taken)) Logging.logMessage("[CraftingForgeJob] Mold batch material restore failed item=" + recipe.firstInput.itemStringID);
				break;
			}
		}
		if (Logging.logEnabled) Logging.logMessage("[CraftingForgeJob] Mold batch forge=" + forge.tileX + "," + forge.tileY
				+ " crafts=" + crafts + " moldUses=" + durabilityUses
				+ " unbreaking=" + MoldItem.hasUnbreaking(mold));
		return crafts;
	}

	/** Adds more of the *same* consumable input, never another recipe or mold. */
	private static int preloadSingleInputBatch(CraftingTaskBoardObjectEntity board, CraftingTask task,
			CraftingTaskRecipe recipe, CraftingStoragePool pool, ProcessingForgeObjectEntity forge, InventoryItem output, int inputSlot) {
		if (task == null || output == null || output.getAmount() <= 0) return 1;
		String inputID;
		int perCraft;
		if (recipe.type == CraftingTaskRecipe.Type.CUSTOM_FORGE) {
			ForgeCookingRecipe custom = recipe.forgeRecipe;
			if (custom == null || custom.secondInput != null || custom.firstInput == null
					|| custom.firstInput.resultBehavior != ForgeCookingInput.ResultBehavior.CONSUME) return 1;
			inputID = custom.firstInput.itemStringID;
			perCraft = custom.firstInput.amount;
		} else if (recipe.type == CraftingTaskRecipe.Type.VANILLA_FORGE) {
			if (recipe.recipe == null || recipe.recipe.ingredients.length != 1) return 1;
			Ingredient ingredient = recipe.recipe.ingredients[0];
			perCraft = ingredient.getIngredientAmount();
			InventoryItem loaded = forge.inventory.getItem(forge.fuelSlots + inputSlot);
			if (loaded == null || !ingredient.matchesItem(loaded.item)) return 1;
			inputID = loaded.item.getStringID();
		} else return 1;
		if (perCraft <= 0) return 1;
		InventoryItem existing = forge.inventory.getItem(forge.fuelSlots + inputSlot);
		if (existing == null || !inputID.equals(existing.item.getStringID())) return 1;
		int outstanding;
		if (task.conditionType == CraftingTask.CONDITION_CRAFT_UNITS) {
			outstanding = Math.max(0, task.amount - board.getPendingForgeAmount(task.itemID));
		} else {
			// Stock target is not a craft count; avoid excessive production.
			return 1;
		}
		// A single active forge task can fill the input slot up to its real capacity.
		// When multiple forge tasks are queued, preserve rotation by limiting each turn
		// to ten crafts. The per-slot canAddItem check below remains authoritative.
		int activeForgeTasks = 0;
		for (CraftingTask queued : board.getTasks()) {
			if (queued.sourceType == CraftingTask.SOURCE_FORGE && !queued.paused && queued.amount > 0) {
				activeForgeTasks++;
				if (activeForgeTasks > 1) break;
			}
		}
		int demandCrafts = (int)Math.min(Integer.MAX_VALUE,
				((long)outstanding + output.getAmount() - 1L) / output.getAmount());
		int wantedCrafts = activeForgeTasks <= 1 ? demandCrafts : Math.min(10, demandCrafts);
		// Avoid unbounded per-item work if mods introduce exceptionally large stacks.
		wantedCrafts = Math.min(wantedCrafts, 999);
		if (Logging.logEnabled) Logging.logMessage("[CraftingForgeJob] Preload policy="
				+ (activeForgeTasks <= 1 ? "single-recipe" : "rotating")
				+ " wantedCrafts=" + wantedCrafts + " remainingDemand=" + outstanding);
		int crafts = 1;
		for (; crafts < wantedCrafts; crafts++) {
			if (pool.getExactAmount(inputID) < perCraft) break;
			InventoryItem extra = new InventoryItem(existing.item, perCraft);
			int slot = forge.fuelSlots + inputSlot;
			if (forge.inventory.canAddItem(forge.getLevel(), null, extra, slot, slot, "npcforgebatch") < perCraft) break;
			InventoryItem taken = pool.takeExactItem(inputID, perCraft, false);
			if (taken == null) break;
			if (!forge.inventory.addItem(forge.getLevel(), null, taken, slot, slot, "npcforgebatch")) {
				if (!pool.addInputItemOrdered(taken)) Logging.logMessage("[CraftingForgeJob] Failed to restore unused batch input=" + inputID);
				break;
			}
		}
		return crafts;
	}

	/**
	 * Allow a second, independent consumable input only when the board has exactly
	 * two active Forge recipes and the other is already assigned to this Forge.
	 * Reusable molds, shared input IDs and any possible combined custom recipe
	 * are excluded. The original Forge processing code remains authoritative.
	 */
	private static int getSharedInputSlot(CraftingTaskBoardObjectEntity board,
			CraftingTaskRecipe requested, ProcessingForgeObjectEntity forge) {
		if (board == null || requested == null || forge == null || forge.inputSlots < 2) return -1;
		CraftingTask first = null;
		CraftingTask second = null;
		for (CraftingTask candidate : board.getTasks()) {
			if (candidate.sourceType != CraftingTask.SOURCE_FORGE || candidate.paused || candidate.amount <= 0) continue;
			if (first == null) first = candidate;
			else if (second == null) second = candidate;
			else return -1;
		}
		if (first == null || second == null || first.itemID == second.itemID) return -1;
		if (first.conditionType != CraftingTask.CONDITION_CRAFT_UNITS
				|| second.conditionType != CraftingTask.CONDITION_CRAFT_UNITS) return -1;
		if (requested.getResultItem() == null) return -1;
		int requestedID = requested.getResultItem().item.getID();
		if (requestedID != first.itemID && requestedID != second.itemID) return -1;
		Point point = new Point(forge.tileX, forge.tileY);
		if (board.getSingleForgeAssignmentSlot(point) != 0) return -1;
		int otherOutputID = board.getForgeAssignmentOutput(point);
		if (otherOutputID != (requestedID == first.itemID ? second.itemID : first.itemID)) return -1;
		int occupiedSlot = forge.fuelSlots;
		int freeSlot = forge.fuelSlots + 1;
		if (!forge.inventory.isSlotClear(freeSlot)) return -1;
		InventoryItem existing = forge.inventory.getItem(occupiedSlot);
		if (existing == null) return -1;
		String existingID = existing.item.getStringID();
		CraftingTaskRecipe activeRecipe = CraftingTaskLogic.getForgeTaskRecipe(board, otherOutputID);
		if (activeRecipe == null || !existingID.equals(getSingleConsumableInputID(activeRecipe, null))) return -1;
		String incomingID = getSingleConsumableInputID(requested, existingID);
		if (incomingID == null || incomingID.equals(existingID)) return -1;
		// Prevent two independent inputs from accidentally satisfying a mold or
		// any other custom two-ingredient recipe.
		for (ForgeCookingRecipe combined : ForgeCookingRecipeRegistry.getRecipes()) {
			if (combined.firstInput == null || combined.secondInput == null) continue;
			String a = combined.firstInput.itemStringID;
			String b = combined.secondInput.itemStringID;
			if ((existingID.equals(a) && incomingID.equals(b))
					|| (existingID.equals(b) && incomingID.equals(a))) return -1;
		}
		return forge.canAddOutput(requested.getResultItem().copy()) ? 1 : -1;
	}

	private static String getSingleConsumableInputID(CraftingTaskRecipe recipe, String otherID) {
		if (recipe.type == CraftingTaskRecipe.Type.CUSTOM_FORGE) {
			ForgeCookingRecipe custom = recipe.forgeRecipe;
			if (custom == null || custom.firstInput == null || custom.secondInput != null
					|| custom.firstInput.resultBehavior != ForgeCookingInput.ResultBehavior.CONSUME) return null;
			return custom.firstInput.itemStringID;
		}
		if (recipe.type == CraftingTaskRecipe.Type.VANILLA_FORGE
				&& recipe.recipe != null && recipe.recipe.ingredients.length == 1) {
			// Only allow a known simple vanilla Forge ingredient. Additional match
			// alternatives are still resolved by takeIngredient during loading.
			return recipe.recipe.ingredients[0].matchesItem(ItemRegistry.getItem("sandtile")) ? "sandtile" : null;
		}
		return null;
	}

	private static boolean loadSharedSingleInput(CraftingStoragePool pool,
			ProcessingForgeObjectEntity forge, CraftingTaskRecipe recipe, int relativeSlot) {
		if (relativeSlot != 1 || !forge.inventory.isSlotClear(forge.fuelSlots + relativeSlot)) return false;
		InventoryItem taken;
		if (recipe.type == CraftingTaskRecipe.Type.CUSTOM_FORGE) {
			ForgeCookingRecipe custom = recipe.forgeRecipe;
			if (custom == null || custom.firstInput == null || custom.secondInput != null
					|| custom.firstInput.resultBehavior != ForgeCookingInput.ResultBehavior.CONSUME) return false;
			taken = pool.takeExactItem(custom.firstInput.itemStringID, custom.firstInput.amount, false);
		} else if (recipe.type == CraftingTaskRecipe.Type.VANILLA_FORGE) {
			if (recipe.recipe == null || recipe.recipe.ingredients.length != 1) return false;
		taken = pool.takeIngredient(recipe.recipe.ingredients[0]);
		} else return false;
		if (taken == null) return false;
		int slot = forge.fuelSlots + relativeSlot;
		forge.inventory.setItem(slot, taken);
		forge.inventory.markDirty(slot);
		if (Logging.logEnabled) Logging.logMessage("[CraftingForgeJob] Dual input loaded forge="
				+ forge.tileX + "," + forge.tileY + " slot=" + relativeSlot + " input=" + taken.item.getStringID());
		return true;
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

	/** Server-side, worker-independent fuel supply for an actively assigned forge. */
	public static void refillAssignedForgeFuel(CraftingTaskBoardObjectEntity board, Point forgePoint) {
		if (board == null || forgePoint == null || !board.getLevel().isServer()) return;
		try {
			DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
			if (station == null) return;
			ProcessingForgeObjectEntity forge = station.getLinkedForge(forgePoint);
			if (forge == null || forge.fuelSlots <= 0) return;
			// Only replenish the fuel inventory when it is empty. Never touch recipe inputs.
			if (ForgeHeatSystem.hasStoredFuel(forge)) return;
			CraftingStoragePool pool = CraftingTaskLogicBridge.getStoragePool(board);
			if (!pool.hasInputs() || pool.getExactAmount(CHARCOAL_ID) <= 0) return;
			Item charcoal = ItemRegistry.getItem(CHARCOAL_ID);
			if (charcoal == null) {
				Logging.logMessage("[CraftingForgeFuel] Charcoal item registry entry missing");
				return;
			}
			InventoryItem candidate = new InventoryItem(charcoal, 1);
			if (forge.inventory.canAddItem(forge.getLevel(), null, candidate, 0, forge.fuelSlots - 1, "npcforgefuelrefill") < 1) {
				if (Logging.logEnabled) Logging.logMessage("[CraftingForgeFuel] No fuel slot capacity forge=" + forge.tileX + "," + forge.tileY);
				return;
			}
			InventoryItem removed = pool.takeExactItem(CHARCOAL_ID, 1, false);
			if (removed == null) return;
			if (!forge.inventory.addItem(forge.getLevel(), null, removed, 0, forge.fuelSlots - 1, "npcforgefuelrefill")) {
				if (!pool.addInputItemOrdered(removed)) Logging.logMessage("[CraftingForgeFuel] Failed to restore charcoal after fuel insertion failed forge=" + forge.tileX + "," + forge.tileY);
				return;
			}
			forge.inventory.markFullDirty();
			forge.forceNextUpdate();
			if (Logging.logEnabled) Logging.logMessage("[CraftingForgeFuel] Refilled charcoal from linked input storage forge=" + forge.tileX + "," + forge.tileY);
		} catch (Exception ex) {
			Logging.logMessage("[CraftingForgeFuel] Refill failed forge=" + forgePoint.x + "," + forgePoint.y + " error=" + ex);
		}
	}

	/** Moves only finished output. The caller persists remaining demand after each transfer. */
	public static int collectAssignmentAmount(CraftingTaskBoardObjectEntity board, Point forgePoint,
			int outputItemID, int remainingAmount) {
		if (board == null || forgePoint == null || remainingAmount <= 0) return 0;
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null) return 0;
		ProcessingForgeObjectEntity forge = station.getLinkedForge(forgePoint);
		if (forge == null) return 0;
		CraftingStoragePool pool = CraftingTaskLogicBridge.getStoragePool(board);
		Item outputItem = ItemRegistry.getItem(outputItemID);
		if (outputItem == null) {
			if (Logging.logEnabled) Logging.logMessage("[CraftingForgeJob] Unknown output item ID=" + outputItemID);
			return 0;
		}

		int moved = 0;
		for (int slot = forge.fuelSlots + forge.inputSlots;
				slot < forge.inventory.getSize() && moved < remainingAmount; slot++) {
			InventoryItem item = forge.inventory.getItem(slot);
			if (item == null || item.item.getID() != outputItemID) continue;
			int move = Math.min(remainingAmount - moved, item.getAmount());
			if (move <= 0) continue;
			if (!pool.addResultOrdered(item.copy(move))) {
				if (Logging.logEnabled) Logging.logMessage("[CraftingForgeJob] Collection blocked: output storage full"
						+ " forge=" + forge.tileX + "," + forge.tileY + " item=" + outputItemID);
				break;
			}
			item.setAmount(item.getAmount() - move);
			if (item.getAmount() <= 0) forge.inventory.clearSlot(slot);
			else forge.inventory.markDirty(slot);
			moved += move;
		}
		if (moved > 0) {
			forge.inventory.markFullDirty();
			forge.forceNextUpdate();
		}
		return moved;
	}

	/** Returns unused ingredients only after the full assignment is collected. */
	public static void returnAssignmentInputs(CraftingTaskBoardObjectEntity board, Point forgePoint, int inputSlot) {
		if (board == null || forgePoint == null) return;
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		ProcessingForgeObjectEntity forge = station == null ? null : station.getLinkedForge(forgePoint);
		if (forge == null) return;
		CraftingStoragePool pool = CraftingTaskLogicBridge.getStoragePool(board);
		returnInputSlotToPool(pool, forge, inputSlot);
		if (inputSlot == 0 && forge.inputSlots >= 2) {
			InventoryItem possibleMold = forge.inventory.getItem(forge.fuelSlots + 1);
			if (possibleMold != null && possibleMold.item instanceof MoldItem) {
				returnInputSlotToPool(pool, forge, 1);
			}
		}
		forge.inventory.markFullDirty();
		forge.forceNextUpdate();
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

	private static void returnInputSlotToPool(CraftingStoragePool pool, ProcessingForgeObjectEntity forge, int inputSlot) {
		if (inputSlot < 0 || inputSlot >= forge.inputSlots) return;
		int slot = forge.fuelSlots + inputSlot;
		InventoryItem item = forge.inventory.getItem(slot);
		if (item == null) return;
		if (pool.addInputItemOrdered(item.copy())) forge.inventory.clearSlot(slot);
		else Logging.logMessage("[CraftingForgeJob] Could not return unused slot input forge="
				+ forge.tileX + "," + forge.tileY + " slot=" + inputSlot);
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
