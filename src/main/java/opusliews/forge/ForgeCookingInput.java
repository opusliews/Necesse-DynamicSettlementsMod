package opusliews.forge;

import necesse.engine.registries.ItemRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import opusliews.durability.DurabilityAction;
import opusliews.durability.DurabilityContext;
import opusliews.durability.ItemDurabilitySystem;

public class ForgeCookingInput {
	public enum ResultBehavior {
		CONSUME,
		KEEP,
		REPLACE,
		DURABILITY_USE
	}

	public final String itemStringID;
	public final int amount;
	public final ResultBehavior resultBehavior;
	public final String replacementItemStringID;
	public final int replacementAmount;

	private ForgeCookingInput(String itemStringID, int amount, ResultBehavior resultBehavior, String replacementItemStringID, int replacementAmount) {
		this.itemStringID = itemStringID;
		this.amount = amount;
		this.resultBehavior = resultBehavior;
		this.replacementItemStringID = replacementItemStringID;
		this.replacementAmount = replacementAmount;
	}

	public static ForgeCookingInput consume(String itemStringID, int amount) {
		return new ForgeCookingInput(itemStringID, amount, ResultBehavior.CONSUME, null, 0);
	}

	public static ForgeCookingInput keep(String itemStringID, int amount) {
		return new ForgeCookingInput(itemStringID, amount, ResultBehavior.KEEP, null, 0);
	}

	public static ForgeCookingInput replace(String itemStringID, int amount, String replacementItemStringID, int replacementAmount) {
		return new ForgeCookingInput(itemStringID, amount, ResultBehavior.REPLACE, replacementItemStringID, replacementAmount);
	}

	public static ForgeCookingInput durabilityUse(String itemStringID) {
		return new ForgeCookingInput(itemStringID, 1, ResultBehavior.DURABILITY_USE, null, 0);
	}

	public boolean matches(InventoryItem item) {
		return item != null && item.item.getStringID().equals(itemStringID) && item.getAmount() >= amount;
	}

	public InventoryItem apply(InventoryItem item, DurabilityContext context) {
		if (item == null) return null;

		switch (resultBehavior) {
			case KEEP:
				return item;
			case REPLACE:
				Item replacement = ItemRegistry.getItem(replacementItemStringID);
				return replacement == null ? null : new InventoryItem(replacement, replacementAmount);
			case DURABILITY_USE:
				return ItemDurabilitySystem.applyActionAndGetItem(item, DurabilityAction.CRAFTING_USE, context);
			case CONSUME:
			default:
				item.setAmount(item.getAmount() - amount);
				return item.getAmount() <= 0 ? null : item;
		}
	}
}
