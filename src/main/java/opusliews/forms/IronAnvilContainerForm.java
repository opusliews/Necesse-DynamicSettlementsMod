package opusliews.forms;

import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormProgressBar;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.forms.position.FormFixedPosition;
import necesse.gfx.forms.presets.containerComponent.object.CraftingStationContainerForm;
import opusliews.container.IronAnvilContainer;

public class IronAnvilContainerForm extends CraftingStationContainerForm {
	private static final int PANEL_WIDTH = 62;
	private static final int PANEL_HEIGHT = 76;
	private static final int PANEL_GAP = 12;

	public IronAnvilContainerForm(Client client, IronAnvilContainer container) {
		super(client, container);

		int vanillaWidth = craftingForm.getWidth();
		int panelX = vanillaWidth + PANEL_GAP;

		craftingForm.setWidth(vanillaWidth + PANEL_GAP + PANEL_WIDTH + 4);

		Form outputPanel = craftingForm.addComponent(new Form("ironAnvilOutput", PANEL_WIDTH, PANEL_HEIGHT));
		outputPanel.setPosition(new FormFixedPosition(panelX, 92));
		outputPanel.addComponent(new FormContainerSlot(client, container, container.OUTPUT_SLOT, 11, 8));
		outputPanel.addComponent(new FormProgressBar(9, 54, 44, false) {
			@Override
			public float getProgress() {
				return container.getCraftProgress();
			}
		});
	}
}
