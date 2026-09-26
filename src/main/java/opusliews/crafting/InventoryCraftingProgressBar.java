package opusliews.crafting;

import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormProgressBar;
import necesse.inventory.container.Container;

public class InventoryCraftingProgressBar extends FormProgressBar {
	private final Client client;

	public InventoryCraftingProgressBar(Client client, int x, int y, int width) {
		super(x, y, width, false);
		this.client = client;
	}

	@Override
	public float getProgress() {
		Container container = client.getInventoryContainer();
		return InventoryCraftingTime.getProgress(container);
	}
}