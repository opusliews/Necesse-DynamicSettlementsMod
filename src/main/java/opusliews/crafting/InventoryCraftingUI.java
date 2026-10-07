package opusliews.crafting;

import necesse.engine.GlobalData;
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
	private static MainGameFormManager boundManager;
	private static Client boundClient;
	private static Container boundContainer;
	private static Boolean lastHidden;

	private InventoryCraftingUI() {
	}

	public static void setup(MainGameFormManager manager, Client client) {
		rebind(manager, client, false, "setup");
	}

	public static void onInventoryFormUpdated(MainGameFormManager manager, Client client) {
		if (manager == null) {
			Logging.logMessage("[InventoryCraftingUI] Inventory form update received with null manager");
			return;
		}
		if (client == null) {
			Logging.logMessage("[InventoryCraftingUI] Inventory form update received with null client");
			return;
		}

		Container current = client.getInventoryContainer();
		if (boundManager == manager && boundClient == client && boundContainer == current
				&& outputPanel != null && !outputPanel.isDisposed()) {
			if (Logging.logEnabled) {
				Logging.logMessage("[InventoryCraftingUI] Inventory form updated but crafting UI is already bound to current container "
						+ InventoryCraftingTime.describeContainer(current));
			}
			return;
		}

		rebind(manager, client, true, "inventory-form-update");
	}

	public static void updateVisibility(MainGameFormManager manager) {
		if (manager == null) {
			Logging.logMessage("[InventoryCraftingUI] Visibility update skipped: manager is null");
			return;
		}

		if (boundManager == manager && boundClient != null) {
			Container current = boundClient.getInventoryContainer();
			if (current != boundContainer) {
				if (Logging.logEnabled) {
					Logging.logMessage("[InventoryCraftingUI] Detected stale inventory crafting binding during visibility update. bound="
							+ InventoryCraftingTime.describeContainer(boundContainer)
							+ " current=" + InventoryCraftingTime.describeContainer(current));
				}
				rebind(manager, boundClient, true, "visibility-self-heal");
			}
		}

		applyVisibility(manager);
	}

	private static void rebind(MainGameFormManager manager, Client client, boolean refreshRecipes, String reason) {
		if (manager == null) {
			Logging.logMessage("[InventoryCraftingUI] Rebind skipped (" + reason + "): manager is null");
			return;
		}
		if (client == null) {
			Logging.logMessage("[InventoryCraftingUI] Rebind skipped (" + reason + "): client is null");
			return;
		}
		if (manager.crafting == null) {
			Logging.logMessage("[InventoryCraftingUI] Rebind skipped (" + reason + "): manager.crafting is null");
			return;
		}

		Container current = client.getInventoryContainer();
		if (current == null) {
			Logging.logMessage("[InventoryCraftingUI] Rebind skipped (" + reason + "): client inventory container is null");
			return;
		}

		if (Logging.logEnabled) {
			Logging.logMessage("[InventoryCraftingUI] Rebinding inventory crafting UI reason=" + reason
					+ " old=" + InventoryCraftingTime.describeContainer(boundContainer)
					+ " new=" + InventoryCraftingTime.describeContainer(current)
					+ " active=" + InventoryCraftingTime.describeContainer(client.getContainer()));
		}

		removeOutputPanel();

		if (refreshRecipes) {
			try {
				GlobalData.updateRecipes();
				if (Logging.logEnabled) {
					Logging.logMessage("[InventoryCraftingUI] Refreshed crafting recipe components after inventory container replacement");
				}
			} catch (Exception e) {
				Logging.logMessage("[InventoryCraftingUI] Failed to refresh crafting recipe components after inventory container replacement: "
						+ e.getClass().getName() + ": " + e.getMessage());
			}
		}

		int outputSlot = InventoryCraftingTime.getOutputSlot(current);
		if (outputSlot < 0) {
			Logging.logMessage("[InventoryCraftingUI] Rebind failed (" + reason + "): output slot not found for "
					+ InventoryCraftingTime.describeContainer(current));
			boundManager = manager;
			boundClient = client;
			boundContainer = current;
			lastHidden = null;
			return;
		}

		try {
			outputPanel = manager.addComponent(
					new InventoryCraftingOutputForm("inventoryCraftingOutput", OUTPUT_PANEL_WIDTH, OUTPUT_PANEL_HEIGHT, client)
			);
			outputPanel.setPosition(new FormRelativePosition(
					manager.crafting,
					() -> manager.crafting.getWidth() + OUTPUT_PANEL_GAP,
					() -> 0
			));
			outputPanel.addComponent(new FormContainerSlot(client, current, outputSlot, 11, 8));
			outputPanel.addComponent(new InventoryCraftingProgressBar(client, 9, 54, 44));
		} catch (Exception e) {
			Logging.logMessage("[InventoryCraftingUI] Failed to create rebound output panel (" + reason + "): "
					+ e.getClass().getName() + ": " + e.getMessage());
			outputPanel = null;
		}

		boundManager = manager;
		boundClient = client;
		boundContainer = current;
		lastHidden = null;

		if (outputPanel != null && Logging.logEnabled) {
			Logging.logMessage("[InventoryCraftingUI] Output panel bound to "
					+ InventoryCraftingTime.describeContainer(current) + " outputSlot=" + outputSlot);
		}
		applyVisibility(manager);
	}

	private static void removeOutputPanel() {
		if (outputPanel == null) return;

		try {
			if (boundManager != null && !outputPanel.isDisposed()) {
				boundManager.removeComponent(outputPanel);
			}
		} catch (Exception e) {
			Logging.logMessage("[InventoryCraftingUI] Failed to remove stale output panel: "
					+ e.getClass().getName() + ": " + e.getMessage());
		}

		outputPanel = null;
		lastHidden = null;
	}

	private static void applyVisibility(MainGameFormManager manager) {
		if (outputPanel == null) return;

		boolean craftingHidden = manager.crafting == null || manager.crafting.isHidden();
		outputPanel.setHidden(craftingHidden);

		if (lastHidden == null || lastHidden != craftingHidden) {
			if (Logging.logEnabled) {
				Logging.logMessage(
						"[InventoryCraftingUI] visibility changed: craftingHidden=" + craftingHidden
								+ ", outputHidden=" + outputPanel.isHidden()
				);
			}
			lastHidden = craftingHidden;
		}
	}
}
