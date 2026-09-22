package opusliews.forge;

import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.Level;
import opusliews.crafting.CraftingComponentCategories;
import opusliews.crafting.CraftingStoragePool;
import opusliews.object.DynamicCraftingStationObjectEntity;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ForgeRequirementSystem {
	public enum Status {
		NOT_REQUIRED,
		READY,
		NO_LINKED_FORGE,
		NO_FUEL
	}

	private static final String charcoalStringID = "charcoal";
	private static final Set<String> forgeRequirementGroups = new HashSet<>();
	private static final Set<String> forgeFittedComponentTypes = new HashSet<>();
	private static final Set<String> forceForgeRequiredOutputs = new HashSet<>();

	static {
		forgeRequirementGroups.add("dsanyplate");
		forgeRequirementGroups.add("dsanymetalplate");
		forgeRequirementGroups.add("dsanyblade");
		forgeRequirementGroups.add("dsanymetalblade");
		forgeRequirementGroups.add("dsanypickaxehead");
		forgeRequirementGroups.add("dsanyaxehead");
		forgeRequirementGroups.add("dsanyshovelhead");

		forgeFittedComponentTypes.add("plate");
		forgeFittedComponentTypes.add("blade");
		forgeFittedComponentTypes.add("pickaxehead");
		forgeFittedComponentTypes.add("axehead");
		forgeFittedComponentTypes.add("shovelhead");
		forgeFittedComponentTypes.add("sickleblade");
		forgeFittedComponentTypes.add("shearsblade");
		forgeFittedComponentTypes.add("shaft");

		forceForgeRequiredOutputs.add("nail");
		forceForgeRequiredOutputs.add("hinge");
		forceForgeRequiredOutputs.add("metalhook");
		forceForgeRequiredOutputs.add("metalcasing");
		forceForgeRequiredOutputs.add("clockworkmechanism");
	}

	private ForgeRequirementSystem() {
	}

	public static boolean requiresRunningForge(Recipe recipe) {
		if (recipe == null || isArrowRecipe(recipe)) return false;
		if (forceForgeRequiredOutputs.contains(recipe.resultStringID)) return true;

		for (Ingredient ingredient : recipe.ingredients) {
			String ingredientID = ingredient.ingredientStringID;
			if (forgeRequirementGroups.contains(ingredientID)) return true;
			if (ForgeCookingRecipeRegistry.isForgeWorkedIngredient(ingredientID)) return true;

			if (!ingredient.isGlobalIngredient()) {
				Item item = necesse.engine.registries.ItemRegistry.getItem(ingredientID);
				if (item != null && isForgeFittedComponent(item)) return true;
			}
		}

		return false;
	}

	public static Status getStatus(
			DynamicCraftingStationObjectEntity station,
			Recipe recipe,
			Collection inventories
	) {
		if (!requiresRunningForge(recipe)) return Status.NOT_REQUIRED;
		if (station == null || !station.supportsForgeLinks()) return Status.NO_LINKED_FORGE;

		List<ProcessingForgeObjectEntity> forges = station.getValidLinkedForges();
		if (forges.isEmpty()) return Status.NO_LINKED_FORGE;

		for (ProcessingForgeObjectEntity forge : forges) {
			if (ForgeHeatSystem.isRunning(forge) || ForgeHeatSystem.hasStoredFuel(forge)) return Status.READY;
		}

		return hasCharcoal(inventories) ? Status.READY : Status.NO_FUEL;
	}

	public static Status getStatus(
			DynamicCraftingStationObjectEntity station,
			Recipe recipe,
			CraftingStoragePool storagePool
	) {
		if (!requiresRunningForge(recipe)) return Status.NOT_REQUIRED;
		if (station == null || !station.supportsForgeLinks()) return Status.NO_LINKED_FORGE;

		List<ProcessingForgeObjectEntity> forges = station.getValidLinkedForges();
		if (forges.isEmpty()) return Status.NO_LINKED_FORGE;

		for (ProcessingForgeObjectEntity forge : forges) {
			if (ForgeHeatSystem.isRunning(forge) || ForgeHeatSystem.hasStoredFuel(forge)) return Status.READY;
		}

		return storagePool != null && storagePool.getExactAmount(charcoalStringID) > 0 ? Status.READY : Status.NO_FUEL;
	}

	public static boolean ensureRunningForge(
			DynamicCraftingStationObjectEntity station,
			Recipe recipe,
			CraftingStoragePool storagePool,
			long requiredDurationMs
	) {
		if (!requiresRunningForge(recipe)) return true;
		if (station == null || !station.supportsForgeLinks() || !station.getLevel().isServer()) return false;

		List<ProcessingForgeObjectEntity> forges = station.getValidLinkedForges();
		if (forges.isEmpty()) return false;

		long required = Math.max(1L, requiredDurationMs + 250L);
		ProcessingForgeObjectEntity forge = selectForge(forges, required);
		if (forge == null) return false;
		return ensureHeat(forge, storagePool, required);
	}

	public static boolean ensureRunningForge(
			DynamicCraftingStationObjectEntity station,
			PlayerMob player,
			Recipe recipe,
			Collection inventories,
			long requiredDurationMs
	) {
		if (!requiresRunningForge(recipe)) return true;
		if (station == null || !station.supportsForgeLinks() || !station.getLevel().isServer()) return false;

		List<ProcessingForgeObjectEntity> forges = station.getValidLinkedForges();
		if (forges.isEmpty()) return false;

		long required = Math.max(1L, requiredDurationMs + 250L);
		ProcessingForgeObjectEntity forge = selectForge(forges, required);
		if (forge == null) return false;
		return ensureHeat(forge, player, inventories, required);
	}

	private static ProcessingForgeObjectEntity selectForge(List<ProcessingForgeObjectEntity> forges, long requiredDurationMs) {
		ProcessingForgeObjectEntity bestRunning = null;
		int bestRunningTime = -1;

		for (ProcessingForgeObjectEntity forge : forges) {
			int remaining = ForgeHeatSystem.getRemainingFuelTime(forge);
			if (ForgeHeatSystem.isRunning(forge) && remaining >= requiredDurationMs) return forge;
			if (ForgeHeatSystem.isRunning(forge) && remaining > bestRunningTime) {
				bestRunning = forge;
				bestRunningTime = remaining;
			}
		}

		if (bestRunning != null) return bestRunning;
		for (ProcessingForgeObjectEntity forge : forges) {
			if (ForgeHeatSystem.hasStoredFuel(forge)) return forge;
		}
		return forges.get(0);
	}

	private static boolean ensureHeat(
			ProcessingForgeObjectEntity forge,
			CraftingStoragePool storagePool,
			long requiredDurationMs
	) {
		if (forge == null) return false;
		ForgeHeatSystem.ensureFuelUnpaused(forge);

		while (ForgeHeatSystem.getRemainingFuelTime(forge) < requiredDurationMs) {
			if (ForgeHeatSystem.consumeOneFuelUnit(forge)) continue;
			if (storagePool == null) return false;

			InventoryItem fuel = storagePool.takeExactItem(charcoalStringID, 1, false);
			if (fuel == null) return false;
			if (!forge.inventory.addItem(forge.getLevel(), null, fuel, 0, forge.fuelSlots - 1, "npcfrrfuel")) {
				storagePool.addInputItemOrdered(fuel);
				return false;
			}
			if (!ForgeHeatSystem.consumeOneFuelUnit(forge)) return false;
		}

		ForgeHeatSystem.ensureFuelUnpaused(forge);
		forge.forceNextUpdate();
		forge.inventory.markFullDirty();
		return ForgeHeatSystem.isRunning(forge);
	}

	private static boolean ensureHeat(
			ProcessingForgeObjectEntity forge,
			PlayerMob player,
			Collection inventories,
			long requiredDurationMs
	) {
		if (forge == null) return false;
		ForgeHeatSystem.ensureFuelUnpaused(forge);

		while (ForgeHeatSystem.getRemainingFuelTime(forge) < requiredDurationMs) {
			if (ForgeHeatSystem.consumeOneFuelUnit(forge)) continue;
			if (!moveOneCharcoalIntoForge(forge, player, inventories)) return false;
			if (!ForgeHeatSystem.consumeOneFuelUnit(forge)) return false;
		}

		ForgeHeatSystem.ensureFuelUnpaused(forge);
		return ForgeHeatSystem.isRunning(forge);
	}

	private static boolean moveOneCharcoalIntoForge(
			ProcessingForgeObjectEntity forge,
			PlayerMob player,
			Collection inventories
	) {
		if (inventories == null) return false;
		Level level = forge.getLevel();
		InventoryItem oneCharcoal = null;
		Inventory sourceInventory = null;
		int sourceSlot = -1;

		for (Object value : inventories) {
			if (!(value instanceof Inventory)) continue;
			Inventory inventory = (Inventory)value;
			if (inventory == forge.inventory || !inventory.canBeUsedForCrafting()) continue;

			for (int slot = 0; slot < inventory.getSize(); slot++) {
				InventoryItem item = inventory.getItem(slot);
				if (!isCharcoal(item)) continue;
				oneCharcoal = item.copy(1);
				sourceInventory = inventory;
				sourceSlot = slot;
				break;
			}
			if (oneCharcoal != null) break;
		}

		if (oneCharcoal == null || sourceInventory == null) return false;
		if (forge.inventory.canAddItem(level, player, oneCharcoal, 0, forge.fuelSlots - 1, "frrfuel") < 1) return false;

		InventoryItem sourceItem = sourceInventory.getItem(sourceSlot);
		if (!isCharcoal(sourceItem)) return false;
		sourceItem.setAmount(sourceItem.getAmount() - 1);
		if (sourceItem.getAmount() <= 0) {
			sourceInventory.clearSlot(sourceSlot);
		} else {
			sourceInventory.markDirty(sourceSlot);
		}

		InventoryItem added = oneCharcoal.copy(1);
		if (!forge.inventory.addItem(level, player, added, 0, forge.fuelSlots - 1, "frrfuel")) {
			InventoryItem restore = oneCharcoal.copy(1);
			sourceInventory.addItem(level, player, restore, "frrfuelrollback");
			return false;
		}
		return true;
	}

	private static boolean hasCharcoal(Collection inventories) {
		if (inventories == null) return false;
		for (Object value : inventories) {
			if (!(value instanceof Inventory)) continue;
			Inventory inventory = (Inventory)value;
			if (!inventory.canBeUsedForCrafting()) continue;
			for (int slot = 0; slot < inventory.getSize(); slot++) {
				if (isCharcoal(inventory.getItem(slot))) return true;
			}
		}
		return false;
	}

	private static boolean isCharcoal(InventoryItem item) {
		return item != null && charcoalStringID.equals(item.item.getStringID()) && item.getAmount() > 0;
	}

	private static boolean isArrowRecipe(Recipe recipe) {
		String resultID = recipe.resultStringID;
		return resultID != null && (resultID.equals("arrow") || resultID.endsWith("arrow"));
	}

	private static boolean isForgeFittedComponent(Item item) {
		try {
			if (item == null || "woodenblade".equals(item.getStringID()) || "woodenshaft".equals(item.getStringID())) return false;

			necesse.inventory.item.ItemCategory category = CraftingComponentCategories.typeManager.getItemsCategory(item);
			return category != null && forgeFittedComponentTypes.contains(category.stringID);
		} catch (Exception ignored) {
			return false;
		}
	}
}
