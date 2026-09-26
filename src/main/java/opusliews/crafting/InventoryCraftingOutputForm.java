package opusliews.crafting;

import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;

public class InventoryCraftingOutputForm extends Form {
	private final Client client;

	public InventoryCraftingOutputForm(String name, int width, int height, Client client) {
		super(name, width, height);
		this.client = client;
	}

	@Override
	public boolean isHidden() {
		return super.isHidden()
				|| client == null
				|| client.getPlayer() == null
				|| !client.getPlayer().isInventoryExtended();
	}
}