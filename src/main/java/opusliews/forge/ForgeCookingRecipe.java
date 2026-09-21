package opusliews.forge;

import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.engine.registries.ItemRegistry;

public class ForgeCookingRecipe {
	public final String stringID;
	public final ForgeCookingInput firstInput;
	public final ForgeCookingInput secondInput;
	public final String outputItemStringID;
	public final int outputAmount;
	public final int processTime;

	public ForgeCookingRecipe(String stringID, ForgeCookingInput firstInput, ForgeCookingInput secondInput, String outputItemStringID, int outputAmount, int processTime) {
		this.stringID = stringID;
		this.firstInput = firstInput;
		this.secondInput = secondInput;
		this.outputItemStringID = outputItemStringID;
		this.outputAmount = outputAmount;
		this.processTime = processTime;
	}

	public boolean matches(InventoryItem slotA, InventoryItem slotB) {
		return firstInput.matches(slotA) && secondInput.matches(slotB)
				|| firstInput.matches(slotB) && secondInput.matches(slotA);
	}

	public boolean isFirstInputInSlotA(InventoryItem slotA, InventoryItem slotB) {
		return firstInput.matches(slotA) && secondInput.matches(slotB);
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
