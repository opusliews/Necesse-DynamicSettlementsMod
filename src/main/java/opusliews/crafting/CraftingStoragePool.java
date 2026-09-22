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
import necesse.inventory.item.Item;
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

	public int getExactAmount(String itemStringID) {
		if (itemStringID == null) return 0;
		int total = 0;
		for (InventoryRange range : inputs) {
			for (int slot = range.startSlot; slot <= range.endSlot; slot++) {
				InventoryItem item = range.inventory.getItem(slot);
				if (item != null && itemStringID.equals(item.item.getStringID())) total += item.getAmount();
			}
		}
		return total;
	}

	public InventoryItem takeExactItem(String itemStringID, int amount, boolean preferMostDamaged) {
		if (itemStringID == null || amount <= 0 || getExactAmount(itemStringID) < amount) return null;

		if (preferMostDamaged && amount == 1) {
			InventoryRange bestRange = null;
			int bestSlot = -1;
			int bestDurability = Integer.MAX_VALUE;
			for (InventoryRange range : inputs) {
				for (int slot = range.startSlot; slot <= range.endSlot; slot++) {
					InventoryItem item = range.inventory.getItem(slot);
					if (item == null || !itemStringID.equals(item.item.getStringID())) continue;
					int durability = opusliews.durability.ItemDurabilitySystem.isBreakable(item)
							? opusliews.durability.ItemDurabilitySystem.getDurability(item)
							: Integer.MAX_VALUE - 1;
					if (bestRange == null || durability < bestDurability) {
						bestRange = range;
						bestSlot = slot;
						bestDurability = durability;
					}
				}
			}
			if (bestRange != null) {
				InventoryItem source = bestRange.inventory.getItem(bestSlot);
				InventoryItem taken = source.copy(1);
				source.setAmount(source.getAmount() - 1);
				if (source.getAmount() <= 0) bestRange.inventory.clearSlot(bestSlot); else bestRange.inventory.markDirty(bestSlot);
				return taken;
			}
		}

		Item itemType = necesse.engine.registries.ItemRegistry.getItem(itemStringID);
		if (itemType == null) return null;
		InventoryItem result = new InventoryItem(itemType, amount);
		int remaining = amount;
		for (InventoryRange range : inputs) {
			for (int slot = range.startSlot; slot <= range.endSlot && remaining > 0; slot++) {
				InventoryItem source = range.inventory.getItem(slot);
				if (source == null || !itemStringID.equals(source.item.getStringID())) continue;
				int take = Math.min(remaining, source.getAmount());
				source.setAmount(source.getAmount() - take);
				if (source.getAmount() <= 0) range.inventory.clearSlot(slot); else range.inventory.markDirty(slot);
				remaining -= take;
			}
		}
		return remaining == 0 ? result : null;
	}

	public InventoryItem takeIngredient(Ingredient ingredient) {
		if (ingredient == null || ingredient.getIngredientAmount() <= 0) return null;
		int needed = ingredient.getIngredientAmount();
		InventoryItem result = null;
		for (InventoryRange range : inputs) {
			for (int slot = range.startSlot; slot <= range.endSlot && needed > 0; slot++) {
				InventoryItem source = range.inventory.getItem(slot);
				if (source == null || !ingredient.matchesItem(source.item)) continue;
				int take = Math.min(needed, source.getAmount());
				if (result == null) result = source.copy(0);
				result.setAmount(result.getAmount() + take);
				source.setAmount(source.getAmount() - take);
				if (source.getAmount() <= 0) range.inventory.clearSlot(slot); else range.inventory.markDirty(slot);
				needed -= take;
			}
		}
		if (needed <= 0) return result;
		if (result != null) addInputItemOrdered(result);
		return null;
	}

	public boolean addInputItemOrdered(InventoryItem item) {
		if (item == null || item.getAmount() <= 0 || inputs.isEmpty()) return item == null || item.getAmount() <= 0;
		InventoryItem remaining = item.copy();
		for (InventoryRange input : inputs) {
			if (remaining.getAmount() <= 0) break;
			input.inventory.addItem(level, null, remaining, input.startSlot, input.endSlot, "craftinginputreturn");
		}
		return remaining.getAmount() <= 0;
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
