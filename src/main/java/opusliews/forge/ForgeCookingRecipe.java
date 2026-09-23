package opusliews.forge;

import necesse.engine.registries.ItemRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;

public class ForgeCookingRecipe {
	public final String stringID;
	public final ForgeCookingInput firstInput;
	public final ForgeCookingInput secondInput;
	public final String outputItemStringID;
	public final int outputAmount;
	public final int processTime;

	private final float dayFraction;

	public ForgeCookingRecipe(String stringID, ForgeCookingInput firstInput, ForgeCookingInput secondInput, String outputItemStringID, int outputAmount, int processTime) {
		if (firstInput == null && secondInput == null) {
			throw new IllegalArgumentException("Forge cooking recipe must have at least one input: " + stringID);
		}

		this.stringID = stringID;
		this.firstInput = firstInput;
		this.secondInput = secondInput;
		this.outputItemStringID = outputItemStringID;
		this.outputAmount = outputAmount;
		this.processTime = processTime;
		this.dayFraction = -1.0F;
	}

	private ForgeCookingRecipe(String stringID, ForgeCookingInput firstInput, ForgeCookingInput secondInput, String outputItemStringID, int outputAmount, float dayFraction) {
		if (firstInput == null && secondInput == null) {
			throw new IllegalArgumentException("Forge cooking recipe must have at least one input: " + stringID);
		}

		this.stringID = stringID;
		this.firstInput = firstInput;
		this.secondInput = secondInput;
		this.outputItemStringID = outputItemStringID;
		this.outputAmount = outputAmount;
		this.processTime = -1;
		this.dayFraction = dayFraction;
	}

	public static ForgeCookingRecipe fractionOfDay(String stringID, ForgeCookingInput firstInput, ForgeCookingInput secondInput, String outputItemStringID, int outputAmount, float dayFraction) {
		return new ForgeCookingRecipe(
				stringID,
				firstInput,
				secondInput,
				outputItemStringID,
				outputAmount,
				dayFraction
		);
	}

	public int getProcessTime(Level level) {
		if (dayFraction >= 0.0F) {
			return (int)((long)level.getWorldEntity().getDayTimeMax() * 1000L * dayFraction);
		}

		return processTime;
	}

	public boolean matches(InventoryItem slotA, InventoryItem slotB) {
		if (firstInput == null) {
			return secondInput.matches(slotA) || secondInput.matches(slotB);
		}
		if (secondInput == null) {
			return firstInput.matches(slotA) || firstInput.matches(slotB);
		}

		return firstInput.matches(slotA) && secondInput.matches(slotB)
				|| firstInput.matches(slotB) && secondInput.matches(slotA);
	}

	public ForgeCookingInput getInputForSlotA(InventoryItem slotA, InventoryItem slotB) {
		if (!matches(slotA, slotB)) return null;

		if (firstInput == null) {
			return secondInput.matches(slotA) ? secondInput : null;
		}
		if (secondInput == null) {
			return firstInput.matches(slotA) ? firstInput : null;
		}

		return firstInput.matches(slotA) && secondInput.matches(slotB) ? firstInput : secondInput;
	}

	public ForgeCookingInput getInputForSlotB(InventoryItem slotA, InventoryItem slotB) {
		if (!matches(slotA, slotB)) return null;

		if (firstInput == null) {
			return secondInput.matches(slotA) ? null : secondInput;
		}
		if (secondInput == null) {
			return firstInput.matches(slotA) ? null : firstInput;
		}

		return firstInput.matches(slotA) && secondInput.matches(slotB) ? secondInput : firstInput;
	}

	public boolean isFirstInputInSlotA(InventoryItem slotA, InventoryItem slotB) {
		return firstInput != null && getInputForSlotA(slotA, slotB) == firstInput;
	}

	public InventoryItem getOutput() {
		Item item = ItemRegistry.getItem(outputItemStringID);
		return item == null ? null : new InventoryItem(item, outputAmount);
	}

	public int getTaskHash() {
		int hash = ("dynamicsettlements:forge:" + stringID).hashCode();
		return hash == 0 ? 1415926 : hash;
	}
}