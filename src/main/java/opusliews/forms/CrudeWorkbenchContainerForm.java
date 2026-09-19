package opusliews.forms;

import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormProgressBar;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.forms.position.FormFixedPosition;
import necesse.gfx.forms.presets.containerComponent.object.CraftingStationContainerForm;
import opusliews.container.CrudeWorkbenchContainer;

public class CrudeWorkbenchContainerForm extends CraftingStationContainerForm {
	private static final int OUTPUT_PANEL_WIDTH = 62;
	private static final int OUTPUT_PANEL_HEIGHT = 76;
	private static final int OUTPUT_PANEL_GAP = 12;

	public CrudeWorkbenchContainerForm(Client client, CrudeWorkbenchContainer container) {
		super(client, container);

		int vanillaWidth = craftingForm.getWidth();
		craftingForm.setWidth(vanillaWidth + OUTPUT_PANEL_GAP + OUTPUT_PANEL_WIDTH + 4);

		int outputPanelX = vanillaWidth + OUTPUT_PANEL_GAP;
		Form outputPanel = craftingForm.addComponent(new Form("crudeWorkbenchOutput", OUTPUT_PANEL_WIDTH, OUTPUT_PANEL_HEIGHT));
		outputPanel.setPosition(new FormFixedPosition(outputPanelX, 92));
		outputPanel.addComponent(new FormContainerSlot(client, container, container.OUTPUT_SLOT, 11, 8));
		outputPanel.addComponent(new FormProgressBar(9, 54, 44, false) {
			@Override
			public float getProgress() {
				return container.getCraftProgress();
			}
		});
	}
}
