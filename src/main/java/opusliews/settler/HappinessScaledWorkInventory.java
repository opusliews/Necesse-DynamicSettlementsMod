package opusliews.settler;

import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.WorkInventory;
import necesse.inventory.InventoryItem;

import java.util.ListIterator;
import java.util.stream.Stream;

public final class HappinessScaledWorkInventory implements WorkInventory {
	private final HumanMob human;
	private final WorkInventory parent;

	public HappinessScaledWorkInventory(HumanMob human, WorkInventory parent) {
		this.human = human;
		this.parent = parent;
	}

	@Override
	public ListIterator listIterator() {
		return parent.listIterator();
	}

	@Override
	public Iterable items() {
		return parent.items();
	}

	@Override
	public Stream stream() {
		return parent.stream();
	}

	@Override
	public void markDirty() {
		parent.markDirty();
	}

	@Override
	public void add(InventoryItem item) {
		parent.add(item);
	}

	@Override
	public int getCanAddAmount(InventoryItem item) {
		if (item == null || item.getAmount() <= 0) return 0;
		int maxStacks = SettlerHappinessScaling.getScaledStackLimit(
				human,
				SettlerHappinessScaling.vanillaWorkInventoryStacks
		);
		int totalStacks = getTotalItemStacks();
		if (totalStacks >= maxStacks) return 0;

		float currentBrokerValue = 0.0F;
		for (Object value : items()) {
			if (value instanceof InventoryItem) currentBrokerValue += ((InventoryItem)value).getBrokerValue();
		}
		float remainingValue = SettlerHappinessScaling.getScaledBrokerValueLimit(human) - currentBrokerValue;
		if (remainingValue < 0.0F) return 0;

		float singleItemBrokerValue = item.item.getBrokerValue(item);
		if (singleItemBrokerValue <= 0.0F) return item.getAmount();
		int totalItemsToPickUp = Math.min((int)(remainingValue / singleItemBrokerValue), item.getAmount());
		return totalStacks == 0 ? Math.max(totalItemsToPickUp, 1) : totalItemsToPickUp;
	}

	@Override
	public boolean isFull() {
		int maxStacks = SettlerHappinessScaling.getScaledStackLimit(
				human,
				SettlerHappinessScaling.vanillaWorkInventoryStacks
		);
		if (getTotalItemStacks() >= maxStacks) return true;

		float brokerValue = 0.0F;
		for (Object value : items()) {
			if (value instanceof InventoryItem) brokerValue += ((InventoryItem)value).getBrokerValue();
		}
		return brokerValue > SettlerHappinessScaling.getScaledBrokerValueLimit(human);
	}

	@Override
	public int getTotalItemStacks() {
		return parent.getTotalItemStacks();
	}

	@Override
	public boolean isEmpty() {
		return parent.isEmpty();
	}
}
