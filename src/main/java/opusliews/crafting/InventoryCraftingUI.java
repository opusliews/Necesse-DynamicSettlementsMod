package opusliews.crafting;

import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.MainGameFormManager;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.forms.position.FormRelativePosition;
import necesse.inventory.container.Container;
import opusliews.logging.Logging;

public final class InventoryCraftingUI {
	private static final int OUTPUT_PANEL_WIDTH = 62;
	private static final int OUTPUT_PANEL_HEIGHT = 76;
	private static final int OUTPUT_PANEL_GAP = 8;
	private static Form outputPanel;
	private static Boolean lastHidden;

	private InventoryCraftingUI() {
	}

	public static void setup(MainGameFormManager manager, Client client) {
		if (manager.crafting == null) {
			Logging.logMessage("[InventoryCraftingUI] setup skipped: manager.crafting is null");
			return;
		}

		Container container = client.getInventoryContainer();
		int outputSlot = InventoryCraftingTime.getOutputSlot(container);
		if (outputSlot < 0) {
			Logging.logMessage("[InventoryCraftingUI] setup skipped: output slot not found");
			return;
		}

		outputPanel = manager.addComponent(
				new Form("inventoryCraftingOutput", OUTPUT_PANEL_WIDTH, OUTPUT_PANEL_HEIGHT)
		);
		outputPanel.setPosition(new FormRelativePosition(
				manager.crafting,
				() -> manager.crafting.getWidth() + OUTPUT_PANEL_GAP,
				() -> 0
		));
		outputPanel.addComponent(new FormContainerSlot(client, container, outputSlot, 11, 8));
		outputPanel.addComponent(new InventoryCraftingProgressBar(client, 9, 54, 44));

		Logging.logMessage("[InventoryCraftingUI] output panel created, outputSlot=" + outputSlot);
		lastHidden = null;
		updateVisibility(manager);
	}

	public static void updateVisibility(MainGameFormManager manager) {
		if (outputPanel == null) {
			return;
		}

		boolean craftingHidden = manager.crafting == null || manager.crafting.isHidden();
		outputPanel.setHidden(craftingHidden);

		if (lastHidden == null || lastHidden != craftingHidden) {
			Logging.logMessage(
					"[InventoryCraftingUI] visibility changed: craftingHidden=" + craftingHidden
							+ ", outputHidden=" + outputPanel.isHidden()
			);
			lastHidden = craftingHidden;
		}
	}
}
