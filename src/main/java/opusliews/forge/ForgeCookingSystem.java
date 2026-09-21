package opusliews.forge;

import necesse.entity.objectEntity.FueledProcessingInventoryObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import necesse.inventory.InventoryItem;
import opusliews.durability.DurabilityContext;
import opusliews.logging.Logging;

public final class ForgeCookingSystem {
	private ForgeCookingSystem() {
	}

	public static ForgeCookingRecipe findRecipe(ProcessingForgeObjectEntity forge) {
		if (forge == null) return null;
		InventoryItem slotA = forge.inventory.getItem(forge.fuelSlots);
		InventoryItem slotB = forge.inventory.getItem(forge.fuelSlots + 1);

		for (ForgeCookingRecipe recipe : ForgeCookingRecipeRegistry.getRecipes()) {
			if (recipe.matches(slotA, slotB)) return recipe;
		}
		return null;
	}

	public static boolean isCustomInput(InventoryItem item) {
		if (item == null) return false;
		for (ForgeCookingRecipe recipe : ForgeCookingRecipeRegistry.getRecipes()) {
			if (recipe.firstInput.itemStringID.equals(item.item.getStringID()) || recipe.secondInput.itemStringID.equals(item.item.getStringID())) {
				return true;
			}
		}
		return false;
	}

	public static FueledProcessingInventoryObjectEntity.NextProcessTask getNextProcessTask(ProcessingForgeObjectEntity forge) {
		ForgeCookingRecipe recipe = findRecipe(forge);
		return recipe == null ? null : new FueledProcessingInventoryObjectEntity.NextProcessTask(recipe.getTaskHash(), recipe.processTime);
	}

	public static boolean processInput(ProcessingForgeObjectEntity forge) {
		ForgeCookingRecipe recipe = findRecipe(forge);
		if (recipe == null) return false;

		InventoryItem output = recipe.getOutput();
		if (output == null || !forge.canAddOutput(output.copy())) return false;

		int slotAIndex = forge.fuelSlots;
		int slotBIndex = forge.fuelSlots + 1;
		InventoryItem slotA = forge.inventory.getItem(slotAIndex);
		InventoryItem slotB = forge.inventory.getItem(slotBIndex);
		boolean normalOrder = recipe.isFirstInputInSlotA(slotA, slotB);
		DurabilityContext context = DurabilityContext.crafting(forge.getLevel(), null, null);

		InventoryItem newA = normalOrder
				? recipe.firstInput.apply(slotA, context)
				: recipe.secondInput.apply(slotA, context);
		InventoryItem newB = normalOrder
				? recipe.secondInput.apply(slotB, context)
				: recipe.firstInput.apply(slotB, context);

		forge.inventory.setItem(slotAIndex, newA);
		forge.inventory.setItem(slotBIndex, newB);
		forge.inventory.markDirty(slotAIndex);
		forge.inventory.markDirty(slotBIndex);
		forge.addOutput(output);
		Logging.logMessage("[ForgeCooking] Crafted recipe=" + recipe.stringID + " output=" + recipe.outputItemStringID + "x" + recipe.outputAmount);
		return true;
	}
}
