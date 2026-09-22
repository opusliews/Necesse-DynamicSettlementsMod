package opusliews.crafting;

import necesse.inventory.InventoryItem;
import necesse.inventory.recipe.Recipe;
import opusliews.forge.ForgeCookingRecipe;

public final class CraftingTaskRecipe {
	public enum Type {
		STATION,
		VANILLA_FORGE,
		CUSTOM_FORGE
	}

	public final Type type;
	public final Recipe recipe;
	public final ForgeCookingRecipe forgeRecipe;

	private CraftingTaskRecipe(Type type, Recipe recipe, ForgeCookingRecipe forgeRecipe) {
		this.type = type;
		this.recipe = recipe;
		this.forgeRecipe = forgeRecipe;
	}

	public static CraftingTaskRecipe station(Recipe recipe) {
		return new CraftingTaskRecipe(Type.STATION, recipe, null);
	}

	public static CraftingTaskRecipe vanillaForge(Recipe recipe) {
		return new CraftingTaskRecipe(Type.VANILLA_FORGE, recipe, null);
	}

	public static CraftingTaskRecipe customForge(ForgeCookingRecipe recipe) {
		return new CraftingTaskRecipe(Type.CUSTOM_FORGE, null, recipe);
	}

	public boolean isForgeRecipe() {
		return type != Type.STATION;
	}

	public int getResultItemID() {
		InventoryItem result = getResultItem();
		return result == null ? -1 : result.item.getID();
	}

	public InventoryItem getResultItem() {
		if (type == Type.CUSTOM_FORGE) return forgeRecipe == null ? null : forgeRecipe.getOutput();
		return recipe == null ? null : recipe.resultItem.copy(recipe.resultAmount);
	}

	public int getProcessTime() {
		return type == Type.CUSTOM_FORGE && forgeRecipe != null ? forgeRecipe.processTime : 8000;
	}
}
