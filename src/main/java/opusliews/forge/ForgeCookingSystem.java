package opusliews.forge;

import necesse.entity.objectEntity.FueledProcessingInventoryObjectEntity;
import necesse.entity.objectEntity.FueledProcessingTechInventoryObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;
import necesse.inventory.InventoryItem;
import opusliews.durability.DurabilityContext;
import opusliews.logging.Logging;

public final class ForgeCookingSystem {
	private static final String MANAWORKS_FORGE_CLASS = "vdgmana.forge.entity.UpgradedForgeEntity";

	private ForgeCookingSystem() {
	}

	public static boolean isSupportedForge(FueledProcessingTechInventoryObjectEntity entity) {
		if (entity == null) return false;
		if (entity instanceof ProcessingForgeObjectEntity) return true;
		// Class-name check: Manaworks is an optional dependency, never link against its classes.
		return MANAWORKS_FORGE_CLASS.equals(entity.getClass().getName());
	}

	public static final class RecipeMatch {
		public final ForgeCookingRecipe recipe;
		public final int slotA;
		public final int slotB;

		public RecipeMatch(ForgeCookingRecipe recipe, int slotA, int slotB) {
			this.recipe = recipe;
			this.slotA = slotA;
			this.slotB = slotB;
		}
	}

	public static RecipeMatch findMatch(FueledProcessingTechInventoryObjectEntity forge) {
		if (!isSupportedForge(forge) || forge.inputSlots < 2) return null;
		int begin = forge.fuelSlots;
		int end = begin + forge.inputSlots;
		// Preserve recipe priority, including on extended Manaworks inventories.
		for (ForgeCookingRecipe recipe : ForgeCookingRecipeRegistry.getRecipes()) {
			for (int a = begin; a < end; a++) {
				for (int b = a + 1; b < end; b++) {
					if (recipe.matches(forge.inventory.getItem(a), forge.inventory.getItem(b))) {
						return new RecipeMatch(recipe, a, b);
					}
				}
			}
		}
		return null;
	}

	// Existing call sites for vanilla Forge automation remain source-compatible.
	public static ForgeCookingRecipe findRecipe(ProcessingForgeObjectEntity forge) {
		RecipeMatch match = findMatch(forge);
		return match == null ? null : match.recipe;
	}

	public static boolean isCustomInput(InventoryItem item) {
		if (item == null) return false;
		for (ForgeCookingRecipe recipe : ForgeCookingRecipeRegistry.getRecipes()) {
			if (recipe.firstInput != null && recipe.firstInput.itemStringID.equals(item.item.getStringID())) return true;
			if (recipe.secondInput != null && recipe.secondInput.itemStringID.equals(item.item.getStringID())) return true;
		}
		return false;
	}

	public static FueledProcessingInventoryObjectEntity.NextProcessTask getNextProcessTask(FueledProcessingTechInventoryObjectEntity forge) {
		RecipeMatch match = findMatch(forge);
		return match == null ? null : new FueledProcessingInventoryObjectEntity.NextProcessTask(
				match.recipe.getTaskHash(), match.recipe.getProcessTime(forge.getLevel()));
	}

	public static boolean processInput(FueledProcessingTechInventoryObjectEntity forge) {
		RecipeMatch match = findMatch(forge);
		if (match == null) return false;
		ForgeCookingRecipe recipe = match.recipe;
		InventoryItem output = recipe.getOutput();
		if (output == null || !forge.canAddOutput(output.copy())) return false;

		InventoryItem slotA = forge.inventory.getItem(match.slotA);
		InventoryItem slotB = forge.inventory.getItem(match.slotB);
		ForgeCookingInput inputA = recipe.getInputForSlotA(slotA, slotB);
		ForgeCookingInput inputB = recipe.getInputForSlotB(slotA, slotB);
		DurabilityContext context = DurabilityContext.crafting(forge.getLevel(), null, null);

		InventoryItem nextA = inputA == null ? slotA : inputA.apply(slotA, context);
		InventoryItem nextB = inputB == null ? slotB : inputB.apply(slotB, context);
		forge.inventory.setItem(match.slotA, nextA);
		forge.inventory.setItem(match.slotB, nextB);
		forge.inventory.markDirty(match.slotA);
		forge.inventory.markDirty(match.slotB);
		forge.addOutput(output);
		Logging.logMessage("[ForgeCooking] Crafted recipe=" + recipe.stringID + " output=" + recipe.outputItemStringID + "x" + recipe.outputAmount + " entity=" + forge.getClass().getName());
		return true;
	}
}
