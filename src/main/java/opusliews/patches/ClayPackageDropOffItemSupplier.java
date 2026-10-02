package opusliews.patches;

import java.util.function.Supplier;
import necesse.inventory.InventoryItem;

public class ClayPackageDropOffItemSupplier implements Supplier<InventoryItem> {
	private final InventoryItem item;

	public ClayPackageDropOffItemSupplier(InventoryItem item) {
		this.item = item;
	}

	@Override
	public InventoryItem get() {
		return item;
	}
}
