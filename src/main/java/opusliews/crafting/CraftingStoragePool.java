package opusliews.crafting;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryItemsRemoved;
import necesse.inventory.InventoryRange;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;

public final class CraftingStoragePool {
	private final Level level;
	private final ArrayList<InventoryRange> inputs;
	private final ArrayList<InventoryRange> outputs;

	private CraftingStoragePool(Level level, ArrayList<InventoryRange> inputs, ArrayList<InventoryRange> outputs) {
		this.level = level;
		this.inputs = inputs;
		this.outputs = outputs;
	}

	public static CraftingStoragePool from(Level level, List<Point> inputPoints, List<Point> outputPoints) {
		return new CraftingStoragePool(
				level,
				getLinkedStorageRanges(level, inputPoints),
				getLinkedStorageRanges(level, outputPoints)
		);
	}

	public boolean hasInputs() {
		return !inputs.isEmpty();
	}

	public boolean hasOutputs() {
		return !outputs.isEmpty();
	}

	public List<String> getMissingIngredients(Recipe recipe) {
		ArrayList<String> missing = new ArrayList<>();
		if (inputs.isEmpty()) {
			missing.add("Input storage is not linked");
			return missing;
		}

		SimulatedRanges simulated = simulateRanges();
		Set<String> seen = new HashSet<>();
		for (Ingredient ingredient : recipe.ingredients) {
			int needed = ingredient.getIngredientAmount();
			if (needed <= 0) {
				boolean found = false;
				for (InventoryRange range : simulated.inputs) {
					if (ingredient.hasIngredientRange(level, null, range)) {
						found = true;
						break;
					}
				}
				if (!found && seen.add(ingredient.getDisplayName())) missing.add(ingredient.getDisplayName());
				continue;
			}

			int remaining = needed;
			for (InventoryRange range : simulated.inputs) {
				if (remaining <= 0) break;
				remaining -= range.inventory.removeItems(
						level,
						null,
						ingredient,
						null,
						remaining,
						range.startSlot,
						range.endSlot,
						null
				);
			}
			if (remaining > 0 && seen.add(ingredient.getDisplayName())) missing.add(ingredient.getDisplayName());
		}
		return missing;
	}

	public boolean canFitResultAfterIngredients(Recipe recipe, InventoryItem result) {
		if (outputs.isEmpty()) return false;

		SimulatedRanges simulated = simulateRanges();
		if (!removeIngredients(simulated.inputs, recipe, null)) return false;

		InventoryItem remaining = result.copy();
		addResult(simulated.outputs, remaining);
		return remaining.getAmount() <= 0;
	}

	public boolean canFitResult(InventoryItem result) {
		if (outputs.isEmpty()) return false;
		InventoryItem remaining = result.copy();
		for (InventoryRange output : outputs) {
			if (remaining.getAmount() <= 0) break;
			int canAdd = output.inventory.canAddItem(
					level,
					null,
					remaining,
					output.startSlot,
					output.endSlot,
					"anvilcrafting"
			);
			remaining.setAmount(remaining.getAmount() - canAdd);
		}
		return remaining.getAmount() <= 0;
	}

	public boolean removeIngredients(Recipe recipe, ArrayList<InventoryItemsRemoved> usedItems) {
		return removeIngredients(inputs, recipe, usedItems);
	}

	public boolean addResultOrdered(InventoryItem result) {
		if (outputs.isEmpty()) return false;

		IdentityHashMap<Inventory, Inventory> snapshots = new IdentityHashMap<>();
		for (InventoryRange output : outputs) snapshots.computeIfAbsent(output.inventory, Inventory::copy);

		InventoryItem remaining = result.copy();
		addResult(outputs, remaining);
		if (remaining.getAmount() <= 0) return true;

		for (InventoryRange output : outputs) {
			Inventory snapshot = snapshots.get(output.inventory);
			if (snapshot != null) output.inventory.override(snapshot);
		}
		return false;
	}

	private boolean removeIngredients(List<InventoryRange> ranges, Recipe recipe, ArrayList<InventoryItemsRemoved> usedItems) {
		for (Ingredient ingredient : recipe.ingredients) {
			int needed = ingredient.getIngredientAmount();
			if (needed <= 0) continue;

			int remaining = needed;
			for (InventoryRange input : ranges) {
				if (remaining <= 0) break;
				remaining -= input.inventory.removeItems(
						level,
						null,
						ingredient,
						null,
						remaining,
						input.startSlot,
						input.endSlot,
						usedItems
				);
			}
			if (remaining > 0) return false;
		}
		return true;
	}

	private void addResult(List<InventoryRange> ranges, InventoryItem remaining) {
		for (InventoryRange output : ranges) {
			if (remaining.getAmount() <= 0) break;
			output.inventory.addItem(
					level,
					null,
					remaining,
					output.startSlot,
					output.endSlot,
					"anvilcrafting"
			);
		}
	}

	private SimulatedRanges simulateRanges() {
		IdentityHashMap<Inventory, Inventory> copies = new IdentityHashMap<>();
		return new SimulatedRanges(copyRanges(inputs, copies), copyRanges(outputs, copies));
	}

	private static ArrayList<InventoryRange> copyRanges(
			List<InventoryRange> source,
			IdentityHashMap<Inventory, Inventory> copies
	) {
		ArrayList<InventoryRange> result = new ArrayList<>();
		for (InventoryRange range : source) {
			Inventory copy = copies.computeIfAbsent(range.inventory, Inventory::copy);
			result.add(new InventoryRange(copy, range.startSlot, range.endSlot));
		}
		return result;
	}

	private static ArrayList<InventoryRange> getLinkedStorageRanges(Level level, List<Point> points) {
		ArrayList<InventoryRange> ranges = new ArrayList<>();
		for (Point point : points) {
			InventoryRange range = getLinkedStorageRange(level, point);
			if (range != null) ranges.add(range);
		}
		return ranges;
	}

	public static InventoryRange getLinkedStorageRange(Level level, Point point) {
		if (point == null) return null;

		LevelObject object = level.getLevelObject(point.x, point.y);
		LevelObject master = object == null ? null : (LevelObject)object.getMasterLevelObject().orElse(null);
		if (master == null) return null;

		ObjectEntity entity = master.getObjectEntity();
		if (!(entity instanceof OEInventory)) return null;

		OEInventory inventory = (OEInventory)entity;
		InventoryRange range = inventory.getSettlementStorage();
		if (range != null) return range;

		Inventory raw = inventory.getInventory();
		return raw == null ? null : new InventoryRange(raw);
	}

	private static class SimulatedRanges {
		private final ArrayList<InventoryRange> inputs;
		private final ArrayList<InventoryRange> outputs;

		private SimulatedRanges(ArrayList<InventoryRange> inputs, ArrayList<InventoryRange> outputs) {
			this.inputs = inputs;
			this.outputs = outputs;
		}
	}
}
